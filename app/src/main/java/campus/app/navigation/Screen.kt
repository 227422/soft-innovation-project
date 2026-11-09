package campus.app.navigation

/**
 * 页面路由。
 *
 * 骨架阶段用密封类型 + 状态切换实现，暂不引入 navigation-compose，
 * 目的是让第一次构建不额外拉依赖。后续需要返回栈时再替换，
 * 页面本身不用改。
 */
sealed interface Screen {
    data object Auth : Screen
    data object ProfileSetup : Screen
    data object Home : Screen
    data object Mine : Screen
    data class Detail(val noticeId: String) : Screen
}
