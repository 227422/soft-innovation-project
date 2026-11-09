package campus.app.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON 读取工具。
 *
 * 只用树形 API，不引入序列化编译器插件，避免改动构建链。
 * 所有取值都是"安全取值"：类型不符或缺失一律返回 null，
 * 由上层决定是"未知"还是"空"，绝不悄悄补默认值。
 */
object JsonKit {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseObject(text: String): JsonObject? = runCatching {
        json.parseToJsonElement(text).jsonObject
    }.getOrNull()

    fun parseElement(text: String): JsonElement? = runCatching {
        json.parseToJsonElement(text)
    }.getOrNull()
}

fun JsonObject.raw(key: String): JsonElement? {
    val e = this[key] ?: return null
    return if (e is JsonNull) null else e
}

fun JsonObject.str(key: String): String? =
    raw(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

fun JsonObject.int(key: String): Int? = str(key)?.toIntOrNull()

fun JsonObject.long(key: String): Long? = str(key)?.toLongOrNull()

fun JsonObject.double(key: String): Double? = str(key)?.toDoubleOrNull()

fun JsonObject.bool(key: String): Boolean? = str(key)?.toBooleanStrictOrNull()

fun JsonObject.obj(key: String): JsonObject? =
    raw(key)?.let { runCatching { it.jsonObject }.getOrNull() }

fun JsonObject.array(key: String): List<JsonElement> =
    (raw(key) as? JsonArray)?.toList() ?: emptyList()

fun JsonObject.strList(key: String): List<String> =
    array(key).mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }

fun JsonObject.objList(key: String): List<JsonObject> =
    array(key).mapNotNull { runCatching { it.jsonObject }.getOrNull() }

/** 兼容后端把分页结果放在 items / notices / data 三种键名下的情况。 */
fun JsonObject.items(): List<JsonObject> {
    val direct = objList("items")
    if (direct.isNotEmpty()) return direct
    val nested = obj("data")
    if (nested != null) {
        val nestedItems = nested.objList("items")
        if (nestedItems.isNotEmpty()) return nestedItems
    }
    return objList("notices")
}
