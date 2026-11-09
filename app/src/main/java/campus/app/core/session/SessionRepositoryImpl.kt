package campus.app.core.session

import campus.app.core.network.ApiClient
import campus.app.core.network.ApiError
import campus.app.core.network.ApiResult
import campus.app.core.network.PersistentCookieJar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 真实会话实现。
 *
 * 关键行为（对应契约 M01 与错误表）：
 * - 启动：先 Loading，再请求 /v1/auth/session；
 * - 只有服务端明确 401 session_invalid 才回登录页；
 * - 网络失败（超时、断网、503）落到 [SessionState.Failed]，可重试，且不清 Cookie；
 * - 登录成功后持久保存 Cookie 与 CSRF 令牌，不保存明文密码。
 */
class SessionRepositoryImpl(
    private val api: ApiClient,
    private val cookieJar: PersistentCookieJar,
    private val csrf: CsrfHolder,
) : SessionRepository {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override suspend fun restore() {
        _state.value = SessionState.Loading
        when (val result = api.restoreSession()) {
            is ApiResult.Success -> {
                csrf.update(result.value.csrfToken)
                _state.value = SessionState.LoggedIn(result.value)
            }

            is ApiResult.Failure -> {
                when {
                    result.error.isSessionExpired || result.error is ApiError.InvalidCredentials -> {
                        clearLocal()
                        _state.value = SessionState.LoggedOut
                    }

                    else -> {
                        // 无法确认会话：保留 Cookie，给出可重试状态，不误判为未登录
                        _state.value = SessionState.Failed(messageFor(result.error))
                    }
                }
            }
        }
    }

    override suspend fun login(email: String, password: String): ApiResult<SessionView> {
        val result = api.loginSession(email, password)
        if (result is ApiResult.Success) {
            csrf.update(result.value.csrfToken)
            _state.value = SessionState.LoggedIn(result.value)
        }
        return result
    }

    override suspend fun register(email: String, password: String): ApiResult<SessionView> {
        val result = api.registerSession(email, password)
        if (result is ApiResult.Success) {
            csrf.update(result.value.csrfToken)
            _state.value = SessionState.LoggedIn(result.value)
        }
        return result
    }

    override suspend fun logout() {
        // 服务端撤销失败也要清本地，否则用户"退出不了"
        api.logout()
        clearLocal()
        _state.value = SessionState.LoggedOut
    }

    /** 切换账号 / 退出时必须同时清 Cookie 与 CSRF，避免串号。 */
    fun clearLocal() {
        cookieJar.clear()
        csrf.clear()
    }

    private fun messageFor(error: ApiError): String = when (error) {
        is ApiError.Unavailable -> "网络不可用，暂时无法确认登录状态，可重试"
        ApiError.RateLimited -> "操作过于频繁，请稍后再试"
        is ApiError.Unknown -> "服务返回了无法识别的结果，可重试"
        else -> "暂时无法确认登录状态，可重试"
    }
}
