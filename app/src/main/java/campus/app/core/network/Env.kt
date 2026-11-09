package campus.app.core.network

/**
 * 运行环境。
 *
 * 契约（M13）要求：样例数据模式与真实服务模式必须由显式开关区分，
 * 禁止"后台请求失败后偷偷改用假数据"。因此这里只有三档，且互斥。
 */
enum class AppEnv(val label: String) {
    MOCK("样例数据"),
    LOCAL("本机后端"),
    REAL("正式服务"),
}

object Env {

    /** 当前环境。接后端时改这一行（或后续接到设置页）。 */
    var current: AppEnv = AppEnv.MOCK
        private set

    fun switchTo(env: AppEnv) {
        current = env
    }

    /**
     * 本机后端地址。
     * 真机联调用 `adb reverse tcp:8000 tcp:8000` 把手机请求转到电脑上，
     * 因此这里用 127.0.0.1，而不是电脑的局域网 IP。
     */
    const val LOCAL_BASE_URL: String = "http://127.0.0.1:8000"

    /** 正式服务地址，待后端部署后填写。 */
    const val REAL_BASE_URL: String = "https://example.invalid"

    val baseUrl: String
        get() = when (current) {
            AppEnv.MOCK -> ""
            AppEnv.LOCAL -> LOCAL_BASE_URL
            AppEnv.REAL -> REAL_BASE_URL
        }

    val usesMockData: Boolean
        get() = current == AppEnv.MOCK
}
