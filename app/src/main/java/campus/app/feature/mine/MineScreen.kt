package campus.app.feature.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import campus.app.core.LocalAppContainer
import campus.app.core.model.Category
import campus.app.core.model.FeedPage
import campus.app.core.model.NoticeItem
import campus.app.core.network.ApiResult
import campus.app.core.network.Env
import campus.app.core.ui.describe
import kotlinx.coroutines.launch

/**
 * 「我的」页：资料摘要、已关注、已忽略、退出登录。
 *
 * 契约要点：
 * - 忽略可撤销（回到列表点"取消忽略"）；
 * - 退出登录必须清 Cookie 与 CSRF，换账号不能看到上一个账号的内容；
 * - 星标不代表已报名，文案里明确说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var starred by remember { mutableStateOf<List<NoticeItem>>(emptyList()) }
    var ignoredIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var profileText by remember { mutableStateOf("未填写") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun reload() {
        loading = true
        // 关注列表：从推荐流里筛星标项，能直接拿到标题
        when (val feed = container.notice.feed(category = null, offset = 0, limit = 50)) {
            is campus.app.core.data.FeedResult.Fresh -> starred = feed.page.items.filter { it.feedback.starred }
            is campus.app.core.data.FeedResult.Cached -> starred = feed.page.items.filter { it.feedback.starred }
            is campus.app.core.data.FeedResult.Failed -> message = feed.error.describe()
        }
        // 忽略列表：后端只返回 ID
        when (val r = container.feedback.list("ignored")) {
            is ApiResult.Success -> ignoredIds = r.value.map { it.noticeId }
            is ApiResult.Failure -> message = r.error.describe()
        }
        when (val p = container.profile.load()) {
            is ApiResult.Success -> profileText = listOfNotNull(
                p.value.schoolName,
                p.value.collegeName,
                p.value.majorName,
                p.value.grade?.let { "第 $it 年" },
            ).joinToString(" · ").ifBlank { "未填写" }
            is ApiResult.Failure -> Unit
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    fun cancelIgnore(noticeId: String) {
        scope.launch {
            when (val r = container.feedback.setIgnored(noticeId, false)) {
                is ApiResult.Success -> {
                    message = "已取消忽略"
                    reload()
                }
                is ApiResult.Failure -> message = r.error.describe()
            }
        }
    }

    fun cancelStar(noticeId: String) {
        scope.launch {
            when (val r = container.feedback.setStarred(noticeId, false)) {
                is ApiResult.Success -> {
                    message = "已取消关注"
                    reload()
                }
                is ApiResult.Failure -> message = r.error.describe()
            }
        }
    }

    fun logout() {
        scope.launch {
            container.session.logout()
            onLoggedOut()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("我的身份", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(profileText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEditProfile) { Text("修改资料") }
                        OutlinedButton(onClick = { logout() }) { Text("退出登录") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("已关注") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("已忽略") })
            }

            if (loading) {
                Text(
                    "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (tab == 0) {
                if (starred.isEmpty()) {
                    EmptyHint("还没有关注任何通知")
                } else {
                    starred.forEach { item ->
                        RowItem(
                            title = item.title,
                            subtitle = "截止：${item.deadline.displayText ?: "待确认"}",
                            actionLabel = "取消关注",
                            onAction = { cancelStar(item.noticeId) },
                        )
                    }
                }
            } else {
                if (ignoredIds.isEmpty()) {
                    EmptyHint("没有已忽略的通知")
                } else {
                    ignoredIds.forEach { id ->
                        RowItem(
                            title = id,
                            subtitle = "已忽略，不再出现在推荐中",
                            actionLabel = "取消忽略",
                            onAction = { cancelIgnore(id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "关注与忽略只影响显示，不代表已报名",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "当前环境：${Env.current.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowItem(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}
