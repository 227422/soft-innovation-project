package campus.app.core.session

import campus.app.core.network.ApiError
import campus.app.core.network.ApiResult
import campus.app.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CSRF 令牌持有者。
 *
 * 单独抽出来是为了打破循环依赖：
 * ApiClient 需要在写请求时读令牌，而令牌由 SessionRepository 写入。
 */
class CsrfHolder(private val store: KeyValueStore) {

    var token: String? = store.getString(KEY)
        private set

    fun update(value: String?) {
        token = value?.takeIf { it.isNotBlank() }
        if (token == null) store.remove(KEY) else store.putString(KEY, token)
    }

    fun clear() = update(null)

    private companion object {
        const val KEY = "session.csrf"
    }
}
