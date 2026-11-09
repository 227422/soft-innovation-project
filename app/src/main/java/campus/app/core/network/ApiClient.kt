package campus.app.core.network

import campus.app.core.model.CatalogNode
import campus.app.core.model.Explanation
import campus.app.core.model.FeedPage
import campus.app.core.model.FeedbackState
import campus.app.core.model.GradeOption
import campus.app.core.model.NoticeDetail
import campus.app.core.model.Preferences
import campus.app.core.model.Profile
import campus.app.core.model.Reminder
import campus.app.core.session.SessionView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * HTTP 客户端。只做四件事：
 * 1. 拼请求（含 Cookie、CSRF、幂等键、版本头）；
 * 2. 非 2xx 统一交给 [ErrorDecoder]；
 * 3. 网络异常映射成 [ApiError.Unavailable]——网络失败不等于空结果；
 * 4. JSON 交给 [Mappers]。
 *
 * 页面不直接用，只通过 Repository。
 */
class ApiClient(
    private val baseUrl: String,
    private val cookieJar: CookieJar,
    private val csrfProvider: () -> String?,
    okHttpClient: OkHttpClient? = null,
) {

    private val client: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---------------------------------------------------------------- 账号

    suspend fun register(email: String, password: String): ApiResult<JsonObject> =
        call("POST", "/v1/auth/register", body = credentials(email, password))

    suspend fun login(email: String, password: String): ApiResult<JsonObject> =
        call("POST", "/v1/auth/login", body = credentials(email, password))

    suspend fun session(): ApiResult<JsonObject> = call("GET", "/v1/auth/session")

    suspend fun logout(): ApiResult<JsonObject> = call("POST", "/v1/auth/logout", csrf = true)

    // ---------------------------------------------------------------- 目录

    suspend fun schools(prefix: String? = null): ApiResult<JsonObject> =
        call("GET", "/v1/catalog/schools", query = prefix?.let { mapOf("prefix" to it) } ?: emptyMap())

    suspend fun colleges(schoolId: String): ApiResult<JsonObject> =
        call("GET", "/v1/catalog/schools/$schoolId/colleges")

    suspend fun majors(schoolId: String, collegeId: String): ApiResult<JsonObject> =
        call("GET", "/v1/catalog/schools/$schoolId/colleges/$collegeId/majors")

    suspend fun majorsOfSchool(schoolId: String): ApiResult<JsonObject> =
        call("GET", "/v1/catalog/schools/$schoolId/majors")

    suspend fun grades(): ApiResult<JsonObject> = call("GET", "/v1/catalog/grades")

    suspend fun preferenceCatalog(): ApiResult<JsonObject> =
        call("GET", "/v1/catalog/preferences")

    // ---------------------------------------------------------------- 资料

    suspend fun profile(): ApiResult<JsonObject> = call("GET", "/v1/me/profile")

    suspend fun saveProfile(fields: Map<String, Any?>, expectedVersion: Int?): ApiResult<JsonObject> {
        val body = buildJsonObject {
            fields.forEach { (key, value) ->
                when (value) {
                    null -> put(key, JsonNull)
                    is Int -> put(key, value)
                    is String -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
        return call("PATCH", "/v1/me/profile", body = body, csrf = true, expectedVersion = expectedVersion)
    }

    suspend fun preferences(): ApiResult<JsonObject> = call("GET", "/v1/me/preferences")

    suspend fun savePreferences(
        fields: Map<String, List<String>>,
        expectedVersion: Int?,
    ): ApiResult<JsonObject> {
        val body = buildJsonObject {
            fields.forEach { (key, values) ->
                put(key, JsonArray(values.map { JsonPrimitive(it) }))
            }
        }
        return call("PATCH", "/v1/me/preferences", body = body, csrf = true, expectedVersion = expectedVersion)
    }

    // ---------------------------------------------------------------- 通知

    suspend fun feed(
        offset: Int = 0,
        limit: Int = 20,
        category: String? = null,
        scope: String? = "recommended",
    ): ApiResult<JsonObject> {
        val query = linkedMapOf(
            "offset" to offset.toString(),
            "limit" to limit.toString(),
        )
        category?.let { query["category"] = it }
        scope?.let { query["scope"] = it }
        return call("GET", "/v1/me/notices", query = query)
    }

    suspend fun noticeDetail(noticeId: String): ApiResult<JsonObject> =
        call("GET", "/v1/me/notices/$noticeId")

    suspend fun explanation(noticeId: String): ApiResult<JsonObject> =
        call("GET", "/v1/me/notices/$noticeId/explanation")

    suspend fun changes(noticeId: String): ApiResult<JsonObject> =
        call("GET", "/v1/me/notices/$noticeId/changes")

    suspend fun sources(): ApiResult<JsonObject> = call("GET", "/v1/sources")

    suspend fun collectionStatus(): ApiResult<JsonObject> = call("GET", "/v1/collection/status")

    // ---------------------------------------------------------------- 反馈

    suspend fun feedback(noticeId: String): ApiResult<JsonObject> =
        call("GET", "/v1/me/notices/$noticeId/feedback")

    suspend fun setFeedback(
        noticeId: String,
        starred: Boolean?,
        ignored: Boolean?,
        expectedVersion: Int?,
    ): ApiResult<JsonObject> {
        val body = buildJsonObject {
            starred?.let { put("starred", it) }
            ignored?.let { put("ignored", it) }
        }
        return call(
            method = "PATCH",
            path = "/v1/me/notices/$noticeId/feedback",
            body = body,
            csrf = true,
            expectedVersion = expectedVersion,
        )
    }

    suspend fun feedbackList(state: String): ApiResult<JsonObject> =
        call("GET", "/v1/me/feedback", query = mapOf("state" to state))

    suspend fun postViewEvent(noticeId: String, eventId: String): ApiResult<JsonObject> {
        val body = buildJsonObject {
            put("notice_id", noticeId)
            put("event_id", eventId)
            put("type", "view")
        }
        return call("POST", "/v1/me/notice-events", body = body, csrf = true)
    }

    // ---------------------------------------------------------------- 提醒

    suspend fun reminders(): ApiResult<JsonObject> = call("GET", "/v1/me/reminders")

    suspend fun writeReminder(
        noticeId: String,
        eventId: String,
        triggerAt: String?,
        enabled: Boolean,
    ): ApiResult<JsonObject> {
        val body = buildJsonObject {
            triggerAt?.let { put("trigger_at", it) }
            put("enabled", enabled)
        }
        return call(
            method = "PUT",
            path = "/v1/me/notices/$noticeId/reminders/$eventId",
            body = body,
            csrf = true,
        )
    }

    // ---------------------------------------------------------------- 类型化封装

    suspend fun loginSession(email: String, password: String): ApiResult<SessionView> =
        login(email, password).map { Mappers.session(it) }

    suspend fun registerSession(email: String, password: String): ApiResult<SessionView> =
        register(email, password).map { Mappers.session(it) }

    suspend fun restoreSession(): ApiResult<SessionView> =
        session().map { Mappers.session(it) }

    suspend fun catalogSchools(prefix: String? = null): ApiResult<List<CatalogNode>> =
        schools(prefix).map { Mappers.catalog(it) }

    suspend fun catalogColleges(schoolId: String): ApiResult<List<CatalogNode>> =
        colleges(schoolId).map { Mappers.catalog(it) }

    suspend fun catalogMajors(schoolId: String, collegeId: String): ApiResult<List<CatalogNode>> =
        majors(schoolId, collegeId).map { Mappers.catalog(it) }

    suspend fun catalogGrades(): ApiResult<List<GradeOption>> = grades().map { Mappers.grades(it) }

    suspend fun myProfile(): ApiResult<Profile> = profile().map { Mappers.profile(it) }

    suspend fun myPreferences(): ApiResult<Preferences> = preferences().map { Mappers.preferences(it) }

    suspend fun feedPage(
        offset: Int = 0,
        limit: Int = 20,
        category: String? = null,
        scope: String? = "recommended",
    ): ApiResult<FeedPage> = feed(offset, limit, category, scope).map { Mappers.feed(it) }

    suspend fun notice(noticeId: String): ApiResult<NoticeDetail> =
        noticeDetail(noticeId).map { Mappers.detail(it) }

    suspend fun noticeExplanation(noticeId: String): ApiResult<Explanation> =
        explanation(noticeId).map { Mappers.explanation(it) }

    suspend fun noticeFeedback(noticeId: String): ApiResult<FeedbackState> =
        feedback(noticeId).map { Mappers.feedbackState(it) }

    suspend fun myReminders(): ApiResult<List<Reminder>> = reminders().map { Mappers.reminders(it) }

    // ---------------------------------------------------------------- 底层

    private fun credentials(email: String, password: String): JsonObject = buildJsonObject {
        put("email", email)
        put("password", password)
    }

    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
        csrf: Boolean = false,
        expectedVersion: Int? = null,
    ): ApiResult<JsonObject> = withContext(Dispatchers.IO) {
        val url = buildUrl(path, query)
        if (url == null) {
            return@withContext ApiResult.Failure(
                ApiError.Unknown("bad_base_url", "服务地址未配置", null),
            )
        }

        val builder = Request.Builder().url(url)

        when (method) {
            "GET" -> builder.get()
            "POST", "PATCH", "PUT", "DELETE" -> {
                val payload = (body ?: JsonObject(emptyMap())).toString()
                builder.method(method, payload.toRequestBody(jsonType))
                builder.header("Content-Type", "application/json")
                builder.header("Idempotency-Key", UUID.randomUUID().toString())
                if (csrf) {
                    csrfProvider()?.let { builder.header("X-CSRF-Token", it) }
                }
                expectedVersion?.let { builder.header("X-Expected-Version", it.toString()) }
            }
            else -> {
                return@withContext ApiResult.Failure(
                    ApiError.Unknown("bad_method", "不支持的请求方法：$method", null),
                )
            }
        }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            return@withContext ApiResult.Failure(ErrorDecoder.fromNetworkError(e.message))
        }

        response.use { resp ->
            val text = try {
                resp.body?.string().orEmpty()
            } catch (e: IOException) {
                ""
            }

            if (resp.isSuccessful) {
                if (text.isBlank()) {
                    return@withContext ApiResult.Success(JsonObject(emptyMap()))
                }
                val parsed = JsonKit.parseObject(text)
                if (parsed == null) {
                    return@withContext ApiResult.Failure(
                        ApiError.Unknown("bad_json", "返回内容不是合法 JSON", null),
                    )
                }
                return@withContext ApiResult.Success(parsed)
            }

            val errorBody = JsonKit.parseObject(text)
            val triple = errorBody?.let { Mappers.errorEnvelope(it) }
            val code = triple?.first
            val message = triple?.second
            val requestId = triple?.third

            val decoded = ErrorDecoder.fromHttp(resp.code, code, message, requestId)
            val finalError = if (decoded is ApiError.VersionConflict) {
                ApiError.VersionConflict(resp.header("X-Current-Version")?.toIntOrNull())
            } else {
                decoded
            }
            ApiResult.Failure(finalError)
        }
    }

    private fun buildUrl(path: String, query: Map<String, String>): HttpUrl? {
        if (baseUrl.isBlank()) return null
        val base = baseUrl.trimEnd('/')
        val full = if (path.startsWith("http")) path else base + path
        val parsed = full.toHttpUrlOrNull() ?: return null
        if (query.isEmpty()) return parsed
        val builder = parsed.newBuilder()
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }
}

/**
 * 保留失败，成功则交给 [transform] 转换；transform 返回 null 视为结构不符，
 * 仍然是失败，而不是"空成功"。
 */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R?): ApiResult<R> = when (this) {
    is ApiResult.Success -> {
        val mapped = transform(value)
        if (mapped == null) {
            ApiResult.Failure(ApiError.Unknown("bad_payload", "返回数据结构不符合预期", null))
        } else {
            ApiResult.Success(mapped)
        }
    }
    is ApiResult.Failure -> this
}
