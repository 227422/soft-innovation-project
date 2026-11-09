package campus.app.core.session

import campus.app.core.network.ApiClient
import campus.app.core.network.ApiError
import campus.app.core.network.ApiResult
import campus.app.core.network.PersistentCookieJar
import campus.app.core.storage.MemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 会话恢复的三条分支（契约 M01）：
 * 200 → 已登录；401 → 未登录；503/超时 → 可重试失败，**不得**当成未登录。
 */
class SessionRecoveryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SessionRepositoryImpl
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var api: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val store = MemoryKeyValueStore()
        val csrf = CsrfHolder(store)
        cookieJar = PersistentCookieJar(store)
        api = ApiClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            cookieJar = cookieJar,
            csrfProvider = { csrf.token },
        )
        repository = SessionRepositoryImpl(api, cookieJar, csrf)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `恢复成功时进入已登录`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"user":{"id":"u-1","email":"a@b.c"},
                     "expires_at":4102444800,
                     "csrf_token":"csrf-1"}
                    """.trimIndent(),
                ),
        )

        repository.restore()

        val state = repository.state.value
        assertTrue(state is SessionState.LoggedIn)
        assertEquals("u-1", (state as SessionState.LoggedIn).session.userId)
    }

    @Test
    fun `401 时回到未登录`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"session_invalid","message":"失效"}}"""),
        )

        repository.restore()

        assertTrue(repository.state.value is SessionState.LoggedOut)
    }

    @Test
    fun `503 时是失败而不是未登录`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"storage_unavailable","message":"稍后重试"}}"""),
        )

        repository.restore()

        val state = repository.state.value
        assertTrue("503 不能被当成已退出登录", state !is SessionState.LoggedOut)
        assertTrue(state is SessionState.Failed)
    }

    @Test
    fun `密码错误时返回明确错误且不改变会话状态`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"invalid_credentials","message":"邮箱或密码错误"}}"""),
        )

        val result = repository.login("a@b.c", "wrong-password")

        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue(error is ApiError.InvalidCredentials)
        assertTrue(error !is ApiError.SessionInvalid)
    }

    @Test
    fun `登录成功会持久保存 Cookie`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Set-Cookie", "campus_session=tok-1; Path=/; Max-Age=2592000")
                .setBody("""{"user":{"id":"u-2","email":"x@y.z"},"expires_at":4102444800,"csrf_token":"c-2"}"""),
        )

        repository.login("x@y.z", "123456")

        assertTrue("Cookie 应已保存", !cookieJar.isEmpty())
        server.takeRequest()
        // 再次请求应自动带上 Cookie
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        api.session()
        val second = server.takeRequest()
        val cookieHeader = second.getHeader("Cookie")
        assertTrue("后续请求应携带会话 Cookie", cookieHeader?.contains("campus_session") == true)
    }
}
