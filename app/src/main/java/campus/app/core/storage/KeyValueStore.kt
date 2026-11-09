package campus.app.core.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * 极简键值存储。
 *
 * 抽出来是为了两件事：
 * 1. 单元测试里可以换成内存实现，不依赖 Android 框架；
 * 2. 将来换 DataStore 或加密存储时，上层不用改。
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun remove(key: String)
}

/** 测试与样例模式用。 */
class MemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}

/** 真机用。所有会话与缓存都落在这里。 */
class PrefsKeyValueStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("zhiyao", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
