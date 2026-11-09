package campus.app.navigation

/**
 * 请求代次闸门：实现契约 M13 的"最后点击生效"。
 *
 * 用法：发起请求前 [next] 取一个号，回调时用 [isCurrent] 判断。
 * 这样 A→B→C 连续打开时，A、B 的迟到响应不会覆盖 C 的界面，
 * 连失败提示和"加载结束"也不允许覆盖。
 *
 * 纯逻辑、无 Android 依赖，可直接单元测试。
 */
class RequestGate {

    private var generation: Int = 0

    /** 开始一次新请求，返回本次令牌。 */
    @Synchronized
    fun next(): Int {
        generation += 1
        return generation
    }

    /** 该令牌是否仍是当前有效的请求。 */
    @Synchronized
    fun isCurrent(token: Int): Boolean = token == generation

    @Synchronized
    fun current(): Int = generation
}
