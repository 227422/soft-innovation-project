package campus.app.core.network

import campus.app.core.ui.describe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错误码解码：确保会话失效与网络失败不会混淆。
 */
class ErrorDecoderTest {

    @Test
    fun `401 且 code 为 invalid_credentials 时是密码错误`() {
        val error = ErrorDecoder.fromHttp(401, "invalid_credentials", "邮箱或密码错误", null)
        assertTrue(error is ApiError.InvalidCredentials)
        assertEquals(false, error.isSessionExpired)
    }

    @Test
    fun `401 无 code 时视为会话失效`() {
        val error = ErrorDecoder.fromHttp(401, null, null, null)
        assertTrue(error is ApiError.SessionInvalid)
        assertTrue(error.isSessionExpired)
    }

    @Test
    fun `503 属于网络类失败，不算会话失效`() {
        val error = ErrorDecoder.fromHttp(503, null, null, null)
        assertTrue(error is ApiError.Unavailable)
        assertEquals(false, error.isSessionExpired)
        assertTrue(error.isNetworkFailure)
    }

    @Test
    fun `409 version_conflict 保留冲突语义`() {
        val error = ErrorDecoder.fromHttp(409, "version_conflict", null, null)
        assertTrue(error is ApiError.VersionConflict)
    }

    @Test
    fun `429 单独识别，不与 AI 限额混用`() {
        assertTrue(ErrorDecoder.fromHttp(429, null, null, null) is ApiError.RateLimited)
    }

    @Test
    fun `无响应按网络失败处理`() {
        val error = ErrorDecoder.fromNetworkError("timeout")
        assertTrue(error is ApiError.Unavailable)
        assertTrue(error.isNetworkFailure)
    }

    @Test
    fun `错误文案区分密码错与会话失效`() {
        val credentialText = ErrorDecoder.fromHttp(401, "invalid_credentials", null, null).describe()
        val sessionText = ErrorDecoder.fromHttp(401, null, null, null).describe()
        assertTrue(credentialText != sessionText)
    }
}
