package campus.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import campus.app.core.LocalAppContainer
import campus.app.core.session.SessionState
import campus.app.feature.auth.AuthScreen
import campus.app.feature.detail.DetailScreen
import campus.app.feature.home.HomeScreen
import campus.app.feature.mine.MineScreen
import campus.app.feature.profile.ProfileSetupScreen

/**
 * 应用根节点：负责会话恢复与页面切换。
 *
 * 启动流程（契约 M01）：先恢复会话再看页面。
 * - 恢复成功 → 首页
 * - 明确失效 → 登录页
 * - 无法确认 → 仍停在登录页，但登录页可重试（不误报密码错）
 */
@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val sessionState by container.session.state.collectAsState()

    var current by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf<Screen>(Screen.Auth)
    }
    val gate = remember { RequestGate() }

    LaunchedEffect(Unit) {
        container.session.restore()
    }

    LaunchedEffect(sessionState) {
        when (sessionState) {
            is SessionState.LoggedIn -> if (current is Screen.Auth) current = Screen.Home
            is SessionState.LoggedOut -> current = Screen.Auth
            else -> Unit
        }
    }

    fun navigateTo(screen: Screen) {
        // 契约 M13：连续打开多个页面时，只有最后一次选择决定界面
        gate.next()
        current = screen
    }

    when (val screen = current) {
        is Screen.Auth -> AuthScreen(
            onAuthenticated = { navigateTo(Screen.ProfileSetup) },
        )

        is Screen.ProfileSetup -> ProfileSetupScreen(
            onDone = { navigateTo(Screen.Home) },
        )

        is Screen.Home -> HomeScreen(
            onOpenNotice = { noticeId -> navigateTo(Screen.Detail(noticeId)) },
            onOpenProfile = { navigateTo(Screen.Mine) },
        )

        is Screen.Mine -> MineScreen(
            onBack = { navigateTo(Screen.Home) },
            onEditProfile = { navigateTo(Screen.ProfileSetup) },
            onLoggedOut = { navigateTo(Screen.Auth) },
        )

        is Screen.Detail -> DetailScreen(
            noticeId = screen.noticeId,
            onBack = { navigateTo(Screen.Home) },
        )
    }
}

/** 简单的状态保存器：只保住"当前在哪个页面"，不带业务数据。 */
private val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, String>(
    save = { screen ->
        when (screen) {
            is Screen.Auth -> "auth"
            is Screen.ProfileSetup -> "profile"
            is Screen.Home -> "home"
            is Screen.Mine -> "mine"
            is Screen.Detail -> "detail:${screen.noticeId}"
        }
    },
    restore = { raw ->
        when {
            raw == "auth" -> Screen.Auth
            raw == "profile" -> Screen.ProfileSetup
            raw == "home" -> Screen.Home
            raw == "mine" -> Screen.Mine
            raw.startsWith("detail:") -> Screen.Detail(raw.removePrefix("detail:"))
            else -> Screen.Auth
        }
    },
)
