package campus.app.core.session

import campus.app.core.network.ApiResult
import campus.app.core.network.ApiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话视图，对应契约 SessionView：
 * user.id、user.email、expires_at、csrf_token。
 * 注意 expires_at 是 Unix 秒，不是毫秒，也不是 ISO 字符串。
 */
data class SessionView(
    val userId: String,
    val email: String,
    val expiresAtEpochSeconds: Long,
    val csrfToken: String,
)

/**
 * 会话状态。
 *
 * 关键区别（契约 M01）：网络失败不能当作会话已失效。
 * 所以 [Failed] 与 [LoggedOut] 是两个不同状态：前者可重试，后者才回登录页。
 */
sealed interface SessionState {
    /** 启动后先进入该状态，避免闪一下登录页。 */
    data object Loading : SessionState

    /** 服务端明确判定未登录 / 会话失效。 */
    data object LoggedOut : SessionState

    data class LoggedIn(val session: SessionView) : SessionState

    /** 网络或服务异常导致无法确认会话，保留可重试入口。 */
    data class Failed(val message: String) : SessionState
}

/**
 * 页面只依赖这个接口，不直接碰 Cookie 存储细节（契约 M13）。
 */
interface SessionRepository {

    val state: StateFlow<SessionState>

    /** 启动时调用：先 Loading，再根据结果落到 LoggedIn / LoggedOut / Failed。 */
    suspend fun restore()

    suspend fun login(email: String, password: String): ApiResult<SessionView>

    suspend fun register(email: String, password: String): ApiResult<SessionView>

    suspend fun logout()
}

/**
 * 样例实现：不联网，用于页面开发阶段。
 * 第 4 步会补上真实实现（Cookie 持久化 + CSRF），页面代码不需要改。
 */
class FakeSessionRepository : SessionRepository {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override suspend fun restore() {
        // 样例模式下默认视为未登录，直接走登录页
        _state.value = SessionState.LoggedOut
    }

    override suspend fun login(email: String, password: String): ApiResult<SessionView> {
        if (email.isBlank() || password.length < 6) {
            return ApiResult.Failure(ApiError.InvalidCredentials)
        }
        val view = SessionView(
            userId = "sample-user-a",
            email = email,
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 30L * 24 * 3600,
            csrfToken = "sample-csrf",
        )
        _state.value = SessionState.LoggedIn(view)
        return ApiResult.Success(view)
    }

    override suspend fun register(email: String, password: String): ApiResult<SessionView> =
        login(email, password)

    override suspend fun logout() {
        _state.value = SessionState.LoggedOut
    }
}
