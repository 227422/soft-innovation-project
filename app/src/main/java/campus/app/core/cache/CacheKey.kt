package campus.app.core.cache

/**
 * 本机缓存键（契约 M13）。
 *
 * 至少包含：服务器环境、user_id、notice_id、content_version、列表查询条件。
 * 这样做的目的：换账号、换环境、通知改版之后，旧缓存不会被当成新结果。
 */
data class CacheKey(
    val serverEnv: String,
    val userId: String,
    val noticeId: String? = null,
    val contentVersion: String? = null,
    val queryFingerprint: String? = null,
) {
    companion object {
        fun list(serverEnv: String, userId: String, queryFingerprint: String): CacheKey =
            CacheKey(serverEnv, userId, queryFingerprint = queryFingerprint)

        fun detail(serverEnv: String, userId: String, noticeId: String, contentVersion: String): CacheKey =
            CacheKey(serverEnv, userId, noticeId = noticeId, contentVersion = contentVersion)
    }
}
