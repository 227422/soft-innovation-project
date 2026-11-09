package campus.app.core.data

import campus.app.core.model.CatalogNode
import campus.app.core.model.Category
import campus.app.core.model.FeedbackState
import campus.app.core.model.FeedPage
import campus.app.core.model.GradeOption
import campus.app.core.model.NoticeDetail
import campus.app.core.model.Preferences
import campus.app.core.model.Profile
import campus.app.core.model.Reminder
import campus.app.core.network.ApiError
import campus.app.core.network.ApiResult

/**
 * 列表结果。
 *
 * 特意把"新数据"与"缓存数据"分成两种成功：
 * 契约要求离线时必须显示缓存时间和离线状态，不能冒充最新结果。
 */
sealed interface FeedResult {
    data class Fresh(val page: FeedPage) : FeedResult
    data class Cached(val page: FeedPage, val lastSyncedAt: String?) : FeedResult
    data class Failed(val error: ApiError) : FeedResult
}

sealed interface DetailResult {
    data class Fresh(val detail: NoticeDetail) : DetailResult
    data class Cached(val detail: NoticeDetail, val cachedAt: String?) : DetailResult
    data class Failed(val error: ApiError) : DetailResult
}

interface ProfileRepository {
    suspend fun schools(prefix: String? = null): ApiResult<List<CatalogNode>>
    suspend fun colleges(schoolId: String): ApiResult<List<CatalogNode>>
    suspend fun majors(schoolId: String, collegeId: String): ApiResult<List<CatalogNode>>
    suspend fun grades(): ApiResult<List<GradeOption>>
    suspend fun load(): ApiResult<Profile>
    suspend fun save(profile: Profile): ApiResult<Profile>
    suspend fun loadPreferences(): ApiResult<Preferences>
    suspend fun savePreferences(preferences: Preferences): ApiResult<Preferences>
}

interface NoticeRepository {
    suspend fun feed(
        category: Category?,
        offset: Int = 0,
        limit: Int = 20,
    ): FeedResult

    suspend fun detail(noticeId: String): DetailResult

    /** 只读缓存，不发请求。用于断网时先出内容。 */
    fun cachedFeed(category: Category?): FeedPage?

    fun cachedDetail(noticeId: String): NoticeDetail?
}

interface FeedbackRepository {
    suspend fun get(noticeId: String): ApiResult<FeedbackState>
    suspend fun setStarred(noticeId: String, starred: Boolean): ApiResult<FeedbackState>
    suspend fun setIgnored(noticeId: String, ignored: Boolean): ApiResult<FeedbackState>
    suspend fun list(state: String): ApiResult<List<FeedbackState>>
}

interface ReminderRepository {
    suspend fun list(): ApiResult<List<Reminder>>
    suspend fun setReminder(
        noticeId: String,
        eventId: String,
        triggerAt: String?,
        enabled: Boolean,
    ): ApiResult<Reminder>
}
