package campus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import campus.app.core.AppContainer
import campus.app.core.LocalAppContainer
import campus.app.navigation.AppRoot
import campus.app.ui.theme.ZhiYaoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = remember { AppContainer(applicationContext) }
            CompositionLocalProvider(LocalAppContainer provides container) {
                ZhiYaoTheme {
                    AppRoot()
                }
            }
        }
    }
}
