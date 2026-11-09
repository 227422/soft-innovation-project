package campus.app.core.network

/**
 * 统一的调用结果。
 *
 * 网络失败（超时、断网）与业务失败（401/409/422）必须分开表达，
 * 因为契约要求"网络失败不等于空结果"，也不能把网络失败误报成密码错误。
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/**
 * 错误分类，对应契约第 15 节的错误表。
 */
sealed interface ApiError {

    /** 401 invalid_credentials：账号或密码错误。界面保留已输入邮箱，不清空。 */
    data object InvalidCredentials : ApiError

    /** 401 session_invalid：会话确认失效，需要重新登录。 */
    data object SessionInvalid : ApiError

    /** 403 csrf_invalid：安全令牌失效。恢复会话后按规则重试，不虚报成功。 */
    data object CsrfInvalid : ApiError

    /** 409 version_conflict：版本冲突。保留草稿，让用户核对新版本，不自动覆盖。 */
    data class VersionConflict(val currentVersion: Int?) : ApiError

    /** 409 key_payload_conflict：同一幂等键但内容不同，停止盲目重试。 */
    data object IdempotencyConflict : ApiError

    /** 422 validation_error：字段不合法。保留输入，标出无效字段。 */
    data class Validation(val message: String, val fields: List<String>) : ApiError

    /** 429：稍后重试。AI 限额与登录频率限制分开处理。 */
    data object RateLimited : ApiError

    /** 503 或无 HTTP 响应：保留旧内容与草稿。 */
    data class Unavailable(val message: String) : ApiError

    /** 其他未识别错误。 */
    data class Unknown(val code: String, val message: String, val requestId: String?) : ApiError

    /** 是否属于"会话已失效"这一唯一需要踢回登录的情形。 */
    val isSessionExpired: Boolean
        get() = this === SessionInvalid

    /** 是否属于网络类失败（可重试，且不应清空本地草稿）。 */
    val isNetworkFailure: Boolean
        get() = this is Unavailable
}

/**
 * 把后端的 {"error":{"code","message","request_id"}} 解码成 [ApiError]。
 * 位置：所有网络异常都必须先经过这里，页面不得直接比较 HTTP 状态码。
 */
object ErrorDecoder {

    fun fromHttp(
        status: Int,
        code: String? = null,
        message: String? = null,
        requestId: String? = null,
    ): ApiError = when {
        status == 401 && code == "invalid_credentials" -> ApiError.InvalidCredentials
        status == 401 -> ApiError.SessionInvalid
        status == 403 && code == "csrf_invalid" -> ApiError.CsrfInvalid
        status == 409 && code == "version_conflict" -> ApiError.VersionConflict(null)
        status == 409 && code == "key_payload_conflict" -> ApiError.IdempotencyConflict
        status == 422 -> ApiError.Validation(message ?: "输入不合法", emptyList())
        status == 429 -> ApiError.RateLimited
        status >= 500 -> ApiError.Unavailable(message ?: "服务暂时不可用")
        else -> ApiError.Unknown(code ?: "http_$status", message ?: "未知错误", requestId)
    }

    fun fromNetworkError(message: String?): ApiError =
        ApiError.Unavailable(message ?: "网络连接失败")
}
