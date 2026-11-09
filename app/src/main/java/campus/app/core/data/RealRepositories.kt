package campus.app.core.data

import campus.app.core.cache.CacheKey
import campus.app.core.model.Category
import campus.app.core.model.FeedbackState
import campus.app.core.model.FeedPage
import campus.app.core.model.NoticeDetail
import campus.app.core.model.Profile
import campus.app.core.model.Reminder
import campus.app.core.network.ApiClient
import campus.app.core.network.ApiError
import campus.app.core.network.ApiResult
import campus.app.core.network.JsonKit
import campus.app.core.network.Mappers
import campus.app.core.storage.KeyValueStore
import java.time.OffsetDateTime

/**
 * 真实实现（Env = LOCAL / REAL）。
 *
 * 缓存策略：首屏（offset=0）成功时把原始 JSON 连同同步时间落盘；
 * 请求失败时若能读到缓存，返回 [FeedResult.Cached] 让界面显示"离线，数据来自缓存"，
 * 而不是把网络失败伪装成空列表。
 */
class RealNoticeRepository(
    private val api: ApiClient,
    private val store: KeyValueStore,
    private val envName: String,
    private val userIdProvider: () -> String?,
) : NoticeRepository {

    override suspend fun feed(category: Category?, offset: Int, limit: Int): FeedResult {
        val result = api.feed(
            offset = offset,
            limit = limit,
            category = category?.name?.lowercase(),
            scope = "recommended",
        )

        return when (result) {
            is ApiResult.Success -> {
                if (offset == 0) {
                    store.putString(cacheKey(category), result.value.toString())
                    store.putString(syncKey(category), OffsetDateTime.now().toString())
                }
                FeedResult.Fresh(Mappers.feed(result.value))
            }

            is ApiResult.Failure -> {
                val cached = if (offset == 0) readCachedFeed(category) else null
                if (cached != null) {
                    FeedResult.Cached(cached, store.getString(syncKey(category)))
                } else {
                    FeedResult.Failed(result.error)
                }
            }
        }
    }

    override suspend fun detail(noticeId: String): DetailResult {
        val result = api.noticeDetail(noticeId)
        return when (result) {
            is ApiResult.Success -> {
                val detail = Mappers.detail(result.value)
                    ?: return DetailResult.Failed(
                        ApiError.Unknown("bad_detail", "详情结构不完整", null),
                    )
                store.putString(detailKey(noticeId), result.value.toString())
                store.putString(syncKey(null, noticeId), OffsetDateTime.now().toString())
                DetailResult.Fresh(detail)
            }

            is ApiResult.Failure -> {
                val cached = cachedDetail(noticeId)
                if (cached != null) {
                    DetailResult.Cached(cached, store.getString(syncKey(null, noticeId)))
                } else {
                    DetailResult.Failed(result.error)
                }
            }
        }
    }

    override fun cachedFeed(category: Category?): FeedPage? = readCachedFeed(category)

    override fun cachedDetail(noticeId: String): NoticeDetail? {
        val raw = store.getString(detailKey(noticeId)) ?: return null
        val obj = JsonKit.parseObject(raw) ?: return null
        return Mappers.detail(obj)
    }

    private fun readCachedFeed(category: Category?): FeedPage? {
        val raw = store.getString(cacheKey(category)) ?: return null
        val obj = JsonKit.parseObject(raw) ?: return null
        return Mappers.feed(obj)
    }

    private fun cacheKey(category: Category?): String {
        val userId = userIdProvider() ?: "anonymous"
        return "feed." + CacheKey.list(envName, userId, "category=${category?.name ?: "all"}")
            .let { "${it.serverEnv}.${it.userId}.${it.queryFingerprint}" }
    }

    private fun syncKey(category: Category?, noticeId: String? = null): String {
        val userId = userIdProvider() ?: "anonymous"
        return if (noticeId != null) {
            "sync.$envName.$userId.detail.$noticeId"
        } else {
            "sync.$envName.$userId.feed.${category?.name ?: "all"}"
        }
    }

    private fun detailKey(noticeId: String): String {
        val userId = userIdProvider() ?: "anonymous"
        return "detail.$envName.$userId.$noticeId"
    }
}

class RealProfileRepository(private val api: ApiClient) : ProfileRepository {

    override suspend fun schools(prefix: String?) = api.catalogSchools(prefix)

    override suspend fun colleges(schoolId: String) = api.catalogColleges(schoolId)

    override suspend fun majors(schoolId: String, collegeId: String) =
        api.catalogMajors(schoolId, collegeId)

    override suspend fun grades() = api.catalogGrades()

    override suspend fun load() = api.myProfile()

    override suspend fun save(profile: Profile): ApiResult<Profile> {
        val fields = mapOf<String, Any?>(
            "school_id" to profile.schoolId,
            "college_id" to profile.collegeId,
            "major_id" to profile.majorId,
            "grade" to profile.grade,
            "education_stage" to profile.educationStage,
        )
        return when (val r = api.saveProfile(fields, profile.version)) {
            is ApiResult.Success -> ApiResult.Success(Mappers.profile(r.value))
            is ApiResult.Failure -> r
        }
    }

    override suspend fun loadPreferences() = api.myPreferences()

    override suspend fun savePreferences(preferences: campus.app.core.model.Preferences) =
        when (val r = api.savePreferences(
            fields = mapOf(
                "course_ids" to preferences.courseIds,
                "skill_ids" to preferences.skillIds,
                "goal_ids" to preferences.goalIds,
                "interest_ids" to preferences.interestIds,
            ),
            expectedVersion = preferences.version,
        )) {
            is ApiResult.Success -> ApiResult.Success(Mappers.preferences(r.value))
            is ApiResult.Failure -> r
        }
}

class RealFeedbackRepository(private val api: ApiClient) : FeedbackRepository {

    override suspend fun get(noticeId: String) = api.noticeFeedback(noticeId)

    override suspend fun setStarred(noticeId: String, starred: Boolean): ApiResult<FeedbackState> {
        val current = api.noticeFeedback(noticeId)
        val version = (current as? ApiResult.Success)?.value?.version
        return api.setFeedback(noticeId, starred = starred, ignored = null, expectedVersion = version)
            .mapState(noticeId)
    }

    override suspend fun setIgnored(noticeId: String, ignored: Boolean): ApiResult<FeedbackState> {
        val current = api.noticeFeedback(noticeId)
        val version = (current as? ApiResult.Success)?.value?.version
        return api.setFeedback(noticeId, starred = null, ignored = ignored, expectedVersion = version)
            .mapState(noticeId)
    }

    override suspend fun list(state: String): ApiResult<List<FeedbackState>> =
        when (val r = api.feedbackList(state)) {
            is ApiResult.Success -> ApiResult.Success(Mappers.feedbackList(r.value))
            is ApiResult.Failure -> r
        }

    private fun ApiResult<kotlinx.serialization.json.JsonObject>.mapState(
        noticeId: String,
    ): ApiResult<FeedbackState> = when (this) {
        is ApiResult.Success -> {
            val state = Mappers.feedbackState(value) ?: FeedbackState(noticeId = noticeId)
            ApiResult.Success(state)
        }
        is ApiResult.Failure -> this
    }
}

class RealReminderRepository(private val api: ApiClient) : ReminderRepository {

    override suspend fun list(): ApiResult<List<Reminder>> =
        when (val r = api.myReminders()) {
            is ApiResult.Success -> r
            is ApiResult.Failure -> r
        }

    override suspend fun setReminder(
        noticeId: String,
        eventId: String,
        triggerAt: String?,
        enabled: Boolean,
    ): ApiResult<Reminder> =
        when (val r = api.writeReminder(noticeId, eventId, triggerAt, enabled)) {
            is ApiResult.Success -> {
                val reminder = Mappers.reminder(r.value)
                if (reminder == null) {
                    ApiResult.Failure(ApiError.Unknown("bad_reminder", "提醒结构不完整", null))
                } else {
                    ApiResult.Success(reminder)
                }
            }
            is ApiResult.Failure -> r
        }
}
