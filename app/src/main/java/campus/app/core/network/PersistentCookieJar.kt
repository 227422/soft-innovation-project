package campus.app.core.network

import campus.app.core.storage.KeyValueStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Cookie 持久化（契约 M01：安卓持久保存 Cookie，登录固定 30 天）。
 *
 * 存储格式：每条一行 `url \t setCookie 文本`。
 * 恢复时用 okhttp 的 [Cookie.parse] 还原，再按 host 过滤，
 * 这样 domain / path / expires / secure 都由 okhttp 自己判断，
 * 不手写匹配规则。
 *
 * 注意：不保存明文密码（契约要求），这里只存会话 Cookie。
 */
class PersistentCookieJar(
    private val store: KeyValueStore,
    private val namespace: String = "cookie",
) : CookieJar {

    private val cache = mutableListOf<Pair<HttpUrl, Cookie>>()

    init {
        cache.addAll(decode(store.getString(keyName()) ?: ""))
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        // 同 name+domain+path 覆盖，避免重复
        cookies.forEach { incoming ->
            cache.removeAll {
                it.second.name == incoming.name &&
                    it.second.domain == incoming.domain &&
                    it.second.path == incoming.path
            }
            cache.add(url to incoming)
        }
        store.putString(keyName(), encode(cache))
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val valid = cache.filter { (_, cookie) ->
            cookie.expiresAt > now && cookie.matches(url)
        }
        // 顺手清理已过期项，避免无限增长
        if (valid.size != cache.size) {
            cache.clear()
            cache.addAll(valid)
            store.putString(keyName(), encode(cache))
        }
        return valid.map { it.second }
    }

    /** 退出登录、切换账号时必须调用，避免会话串号。 */
    fun clear() {
        cache.clear()
        store.remove(keyName())
    }

    fun isEmpty(): Boolean = cache.isEmpty()

    private fun keyName(): String = "zhiyao.$namespace"

    private fun encode(list: List<Pair<HttpUrl, Cookie>>): String =
        list.joinToString("\n") { (url, cookie) -> "${url}\t${cookie}" }

    private fun decode(raw: String): List<Pair<HttpUrl, Cookie>> {
        if (raw.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        return raw.lineSequence()
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val url = line.substring(0, tab).toHttpUrlOrNull()
                    ?: return@mapNotNull null
                val cookie = Cookie.parse(url, line.substring(tab + 1)) ?: return@mapNotNull null
                if (cookie.expiresAt <= now) null else url to cookie
            }
            .toList()
    }
}
