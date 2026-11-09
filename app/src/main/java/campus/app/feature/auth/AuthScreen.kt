package campus.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import campus.app.core.LocalAppContainer
import campus.app.core.network.ApiResult
import campus.app.core.network.Env
import campus.app.core.ui.describe
import kotlinx.coroutines.launch

private enum class AuthMode { LOGIN, REGISTER }

/**
 * 登录 / 注册。
 *
 * 契约要点：
 * - 密码错误保留已输入邮箱，只提示密码问题；
 * - 网络失败与会话失效文案不同；
 * - 请求进行中禁用按钮，避免重复提交。
 */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val validation = when {
            email.isBlank() -> "请输入邮箱"
            !email.contains("@") -> "邮箱格式不正确"
            password.length < 6 -> "密码至少 6 位"
            else -> null
        }
        if (validation != null) {
            errorText = validation
            return
        }
        errorText = null
        loading = true
        scope.launch {
            val result = if (mode == AuthMode.LOGIN) {
                container.session.login(email.trim(), password)
            } else {
                container.session.register(email.trim(), password)
            }
            loading = false
            when (result) {
                is ApiResult.Success -> onAuthenticated()
                is ApiResult.Failure -> errorText = result.error.describe()
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("知要", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "把校内通知，收敛成针对你的一件事",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorText = null },
                label = { Text("邮箱") },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorText = null },
                label = { Text("密码") },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            if (errorText != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { submit() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.height(0.dp))
                } else {
                    Text(if (mode == AuthMode.LOGIN) "登录" else "注册")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                        errorText = null
                    },
                    enabled = !loading,
                ) {
                    Text(if (mode == AuthMode.LOGIN) "没有账号？注册" else "已有账号？登录")
                }
                OutlinedButton(onClick = onAuthenticated, enabled = !loading) {
                    Text("先看看")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "当前环境：${Env.current.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
