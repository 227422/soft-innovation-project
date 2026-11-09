package campus.app.feature.detail

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import campus.app.core.LocalAppContainer
import campus.app.core.data.DetailResult
import campus.app.core.model.NoticeDetail
import campus.app.core.model.QuestionAnswer
import campus.app.core.network.ApiResult
import campus.app.core.ui.UiState
import campus.app.core.ui.describe
import kotlinx.coroutines.launch

/**
 * 详情页：原文 + 四问卡片 + 反馈动作。
 *
 * 契约要点：
 * - 正文不等待 AI：正文先出来，解释后补；
 * - 解释缺失或过时 → 显示"待确认"，不编造；
 * - 星标与忽略互斥，且都不等于"已报名"；
 * - 写操作失败保留当前界面，只提示，不清空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    noticeId: String,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<UiState<NoticeDetail>>(UiState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var starred by remember { mutableStateOf(false) }
    var ignored by remember { mutableStateOf(false) }
    var reminderOn by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(noticeId, reloadKey) {
        state = UiState.Loading
        message = null
        when (val result = container.notice.detail(noticeId)) {
            is DetailResult.Fresh -> {
                state = UiState.Content(result.detail)
                starred = result.detail.item.feedback.starred
                ignored = result.detail.item.feedback.ignored
            }

            is DetailResult.Cached -> {
                state = UiState.Offline(result.detail, result.cachedAt)
                starred = result.detail.item.feedback.starred
                ignored = result.detail.item.feedback.ignored
            }

            is DetailResult.Failed -> {
                state = if (result.error.isSessionExpired) {
                    UiState.ExpiredSession
                } else {
                    UiState.Error(result.error.describe())
                }
            }
        }
    }

    fun toggleStar() {
        if (busy) return
        busy = true
        scope.launch {
            when (val r = container.feedback.setStarred(noticeId, !starred)) {
                is ApiResult.Success -> {
                    starred = r.value.starred
                    ignored = r.value.ignored
                    message = if (starred) "已加入关注" else "已取消关注"
                }
                is ApiResult.Failure -> message = r.error.describe()
            }
            busy = false
        }
    }

    fun toggleIgnore() {
        if (busy) return
        busy = true
        scope.launch {
            when (val r = container.feedback.setIgnored(noticeId, !ignored)) {
                is ApiResult.Success -> {
                    ignored = r.value.ignored
                    starred = r.value.starred
                    message = if (ignored) "已忽略，可在「我的」中撤销" else "已取消忽略"
                }
                is ApiResult.Failure -> message = r.error.describe()
            }
            busy = false
        }
    }

    fun toggleReminder() {
        if (busy) return
        busy = true
        scope.launch {
            val next = !reminderOn
            when (val r = container.reminders.setReminder(noticeId, "deadline", null, next)) {
                is ApiResult.Success -> {
                    reminderOn = r.value.enabled
                    message = if (reminderOn) "已设置截止提醒" else "已取消提醒"
                }
                is ApiResult.Failure -> message = r.error.describe()
            }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知详情") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = { TextButton(onClick = { reloadKey += 1 }) { Text("刷新") } },
            )
        },
        bottomBar = {
            // 动作区固定在底部，长正文滚动时也能操作
            if (state is UiState.Content || state is UiState.Offline) {
                DetailActions(
                    starred = starred,
                    ignored = ignored,
                    reminderOn = reminderOn,
                    busy = busy,
                    message = message,
                    onStar = { toggleStar() },
                    onIgnore = { toggleIgnore() },
                    onRemind = { toggleReminder() },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (val current = state) {
                is UiState.Loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(26.dp), strokeWidth = 2.dp)
                }

                is UiState.Error -> Column {
                    Text(current.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { reloadKey += 1 }) { Text("重试") }
                }

                is UiState.ExpiredSession -> Column {
                    Text("登录状态已失效", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack) { Text("返回") }
                }

                is UiState.Offline -> {
                    OfflineNote(current.lastSyncedAt)
                    current.cached?.let { Content(it) }
                }

                is UiState.Content -> Content(current.value)
                is UiState.Empty -> Unit
            }
        }
    }
}

@Composable
private fun OfflineNote(since: String?) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("当前离线，显示的是缓存内容", style = MaterialTheme.typography.bodySmall)
            Text(
                "缓存时间：${since ?: "未知"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Content(detail: NoticeDetail) {
    val item = detail.item

    Text(
        text = item.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "${item.sourceName ?: "来源未知"} · 发布于 ${item.publishedAt ?: "未知"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (item.titleOnly) Badge("正文受限，仅标题")
        if (item.hasUpdate) Badge("内容有更新")
        if (item.importance.tentative) Badge("结论待确认")
    }

    Spacer(Modifier.height(14.dp))
    HorizontalDivider()
    Spacer(Modifier.height(14.dp))

    // 原文
    if (detail.body.isNullOrBlank()) {
        Text(
            "该通知正文未取得，请打开原文查看。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(detail.body, style = MaterialTheme.typography.bodyMedium)
    }
    detail.sourceUrl?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            "原文链接：$it",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (detail.attachments.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("附件", style = MaterialTheme.typography.titleSmall)
        detail.attachments.forEach { a ->
            Text(
                "· ${a.name}（${a.status ?: "状态未知"}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("这条通知对我意味着什么", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    val explanation = detail.explanation
    if (explanation == null) {
        PendingCard("解释尚未生成", "正文已可阅读，解读稍后补充；不等待 AI 也不影响阅读。")
    } else {
        if (explanation.stale) {
            Text(
                "解读基于旧版本正文，可能已过时",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(6.dp))
        }
        QuestionCard("该不该参加", explanation.four.shouldJoin)
        QuestionCard("还差什么", explanation.four.missing)
        QuestionCard("还剩几天", explanation.four.daysLeft)
        QuestionCard("下一步做什么", explanation.four.nextStep)

        if (explanation.policies.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("依据", style = MaterialTheme.typography.titleSmall)
            explanation.policies.forEach { p ->
                Text(
                    "· ${p.title}${p.clause?.let { "：$it" } ?: ""}${p.updatedAt?.let { "（$it）" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "以上为文件引用，具体以最新文件为准",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun PendingCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuestionCard(question: String, answer: QuestionAnswer) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (answer.pending) {
                    Text(
                        "  待确认",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = answer.answer ?: if (answer.pending) "依据不足，暂时无法确定" else "暂无内容",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (answer.checklist.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                answer.checklist.forEach { row ->
                    Text(
                        "☐ $row",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

@Composable
private fun DetailActions(
    starred: Boolean,
    ignored: Boolean,
    reminderOn: Boolean,
    busy: Boolean,
    message: String?,
    onStar: () -> Unit,
    onIgnore: () -> Unit,
    onRemind: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = starred,
                onClick = onStar,
                enabled = !busy,
                label = { Text(if (starred) "已关注" else "关注") },
            )
            FilterChip(
                selected = ignored,
                onClick = onIgnore,
                enabled = !busy,
                label = { Text(if (ignored) "已忽略" else "忽略") },
            )
            FilterChip(
                selected = reminderOn,
                onClick = onRemind,
                enabled = !busy,
                label = { Text(if (reminderOn) "已提醒" else "截止提醒") },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "关注与忽略只影响显示，不代表已报名",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
