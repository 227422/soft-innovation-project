package campus.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import campus.app.core.LocalAppContainer
import campus.app.core.data.FeedResult
import campus.app.core.model.Category
import campus.app.core.model.FeedPage
import campus.app.core.model.NoticeItem
import campus.app.core.model.UrgencyState
import campus.app.core.network.Env
import campus.app.core.ui.UiState
import campus.app.core.ui.describe
import campus.app.navigation.RequestGate

private data class TabItem(val category: Category, val label: String)

private val tabs = listOf(
    TabItem(Category.COMPETITION, "竞赛"),
    TabItem(Category.POLICY, "政策"),
    TabItem(Category.PUBLICITY, "公示"),
    TabItem(Category.DAILY, "日常"),
)

/**
 * 首页：分类 Tab + 通知列表。
 *
 * 状态严格按契约六态区分：加载 / 内容 / 空 / 离线 / 错误 / 会话失效。
 * 顺序与分数一律用后端返回值，前端不重排、不重算。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenNotice: (String) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val container = LocalAppContainer.current
    val gate = remember { RequestGate() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<UiState<FeedPage>>(UiState.Loading) }

    val category = tabs[selectedTab].category

    LaunchedEffect(selectedTab, reloadKey) {
        val token = gate.next()
        state = UiState.Loading
        val result = container.notice.feed(category = category)
        if (!gate.isCurrent(token)) return@LaunchedEffect

        state = when (result) {
            is FeedResult.Fresh -> UiState.Content(result.page)
            is FeedResult.Cached -> UiState.Offline(result.page, result.lastSyncedAt)
            is FeedResult.Failed -> if (result.error.isSessionExpired) {
                UiState.ExpiredSession
            } else {
                UiState.Error(result.error.describe())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("知要", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "环境：${Env.current.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { reloadKey += 1 }) { Text("刷新") }
                    TextButton(onClick = onOpenProfile) { Text("我的") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (val current = state) {
                is UiState.Loading -> CenterBox {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp), strokeWidth = 3.dp)
                }

                is UiState.Content -> NoticeList(
                    page = current.value,
                    offline = false,
                    offlineSince = null,
                    onOpenNotice = onOpenNotice,
                    onRetry = { reloadKey += 1 },
                )

                is UiState.Offline -> NoticeList(
                    page = current.cached,
                    offline = true,
                    offlineSince = current.lastSyncedAt,
                    onOpenNotice = onOpenNotice,
                    onRetry = { reloadKey += 1 },
                )

                is UiState.Empty -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("该类暂无通知", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "这是空结果，不是加载失败",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is UiState.Error -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(current.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        if (current.retryable) {
                            Button(onClick = { reloadKey += 1 }) { Text("重试") }
                        }
                    }
                }

                is UiState.ExpiredSession -> CenterBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("登录状态已失效", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onOpenProfile) { Text("重新登录入口") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeList(
    page: FeedPage?,
    offline: Boolean,
    offlineSince: String?,
    onOpenNotice: (String) -> Unit,
    onRetry: () -> Unit,
) {
    if (page == null || page.items.isEmpty()) {
        CenterBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (page == null) "暂无可显示的内容" else "该类暂无通知",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry) { Text("重新加载") }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        if (offline) {
            item {
                OfflineBanner(offlineSince)
            }
        }
        items(page.items) { notice ->
            NoticeCard(notice = notice, onClick = { onOpenNotice(notice.noticeId) })
        }
        item {
            Text(
                text = "共 ${page.total} 条 · 策略 ${page.rankingStrategyVersion ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun OfflineBanner(since: String?) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("当前离线，显示的是缓存内容", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "上次同步：${since ?: "未知"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeCard(notice: NoticeItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = notice.ranking.scoreText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(notice.sourceName ?: "来源未知")
                    append(" · ")
                    append(deadlineText(notice))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (notice.deadline.urgency == UrgencyState.CLOSED) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = notice.ranking.reasons.joinToString(" · ").ifBlank { "暂无排序理由" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (notice.hasUpdate || notice.titleOnly || notice.importance.tentative) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (notice.importance.tentative) Badge("待确认")
                    if (notice.titleOnly) Badge("仅标题")
                    if (notice.hasUpdate) Badge("有更新")
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

private fun deadlineText(notice: NoticeItem): String {
    val days = notice.deadline.daysLeft
    val display = notice.deadline.displayText
    return when {
        notice.deadline.urgency == UrgencyState.NONE -> "无需行动"
        notice.deadline.urgency == UrgencyState.CLOSED -> "已截止"
        notice.deadline.urgency == UrgencyState.UNKNOWN -> "截止时间待确认"
        days != null && days >= 0 -> "剩 $days 天" + (display?.let { "（$it）" } ?: "")
        display != null -> display
        else -> "截止时间待确认"
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
