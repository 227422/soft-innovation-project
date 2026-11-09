package campus.app.core.ui

/**
 * 页面统一状态（契约 M13 要求至少区分这六种）。
 *
 * 每个页面都必须显式区分，尤其是：
 * - [Empty] 是"确实没有内容"，不是加载失败；
 * - [Offline] 展示的是本机缓存 + 缓存时间，不能冒充最新数据；
 * - [ExpiredSession] 只在服务端确认失效时使用。
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<T>(val value: T) : UiState<T>

    data object Empty : UiState<Nothing>

    data class Offline<T>(
        val cached: T? = null,
        val lastSyncedAt: String? = null,
    ) : UiState<T>

    data class Error(
        val message: String,
        val retryable: Boolean = true,
    ) : UiState<Nothing>

    data object ExpiredSession : UiState<Nothing>
}
