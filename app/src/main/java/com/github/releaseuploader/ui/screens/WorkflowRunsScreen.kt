package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.WorkflowRun
import com.github.releaseuploader.ui.viewmodel.WorkflowRunsViewModel

/** 工作流运行记录列表页（点击某条运行可看 job 详情） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunsScreen(
    owner: String,
    repo: String,
    workflowId: Long,
    workflowName: String,
    onRunClick: (Long) -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: WorkflowRunsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadRuns(owner, repo, workflowId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workflowName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("错误：${uiState.error}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadRuns(owner, repo, workflowId) }) { Text("重试") }
                    }
                }
                uiState.runs.isEmpty() -> {
                    Text("暂无运行记录", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn {
                        items(uiState.runs, key = { it.id }) { run ->
                            RunRow(run) { onRunClick(run.id) }
                        }
                    }
                }
            }
        }
    }
}

/** 运行列表项（对齐官方 App：提交信息主标题 + 工作流名/#号/SHA + 时长/分支/相对时间标签组） */
@Composable
private fun RunRow(run: WorkflowRun, onClick: () -> Unit) {
    val commitMessage = run.headCommit?.message?.trim()?.lineSequence()?.firstOrNull()
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            val (icon, tint) = runStatusVisual(run.status, run.conclusion)
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        },
        headlineContent = {
            Text(
                text = commitMessage ?: "#${run.runNumber} ${run.name.orEmpty()}".trim(),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = buildString {
                        if (!run.name.isNullOrBlank()) append(run.name)
                        append(" · #${run.runNumber}")
                        if (!run.headSha.isNullOrBlank()) append(" · ${run.headSha.take(7)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 耗时用 run_started_at → updated_at（runs API 无 completed_at，created_at 含排队时间）；
                    // 进行中的 run updatedAt 停在最后更新，不显示"冻结耗时"，由状态徽标体现
                    val duration = if (run.status == "completed") formatDuration(run.runStartedAt, run.updatedAt) else ""
                    if (duration.isNotEmpty()) MetaTag("⏱ $duration")
                    if (!run.headBranch.isNullOrBlank()) MetaTag("🌿 ${run.headBranch}")
                    val ago = timeAgo(run.createdAt)
                    if (ago.isNotEmpty()) MetaTag("📅 $ago")
                }
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

/** 圆角元信息标签（时长/分支/时间） */
@Composable
private fun MetaTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 相对时间："刚刚" / "X分钟前" / "X小时前" / "X天前" / "X个月前" / "X年前" */
fun timeAgo(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val time = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return ""
    val minutes = java.time.Duration.between(time, java.time.Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        minutes < 1440 -> "${minutes / 60}小时前"
        minutes < 43200 -> "${minutes / 1440}天前"
        minutes < 525600 -> "${minutes / 43200}个月前"
        else -> "${minutes / 525600}年前"
    }
}

/** 运行/步骤状态 → 图标+颜色（JobDetail 步骤列表同用） */
fun runStatusVisual(status: String, conclusion: String?): Pair<ImageVector, Color> {
    return when {
        conclusion == "success" -> Icons.Default.CheckCircle to Color(0xFF1F883D)
        conclusion == "failure" || conclusion == "timed_out" -> Icons.Default.Cancel to Color(0xFFCF222E)
        conclusion == "cancelled" -> Icons.Default.Cancel to Color(0xFF6E7781)
        conclusion == "skipped" -> Icons.Default.SkipNext to Color(0xFF6E7781)
        status == "in_progress" -> Icons.Default.RadioButtonUnchecked to Color(0xFFBF8700)
        status == "queued" -> Icons.Default.RadioButtonUnchecked to Color(0xFF0969DA)
        else -> Icons.Default.RadioButtonUnchecked to Color(0xFF6E7781)
    }
}

/**
 * 计算 ISO8601 时间差，格式化为 "X分X秒" / "X小时X分" / "X天X小时"。
 * ⚠️ end 为 null 时返回空字符串（不 fallback 到当前时间——那会变成"距开始多久"，是伪耗时）。
 * 进行中的任务由状态徽标体现，不再显示耗时。
 */
fun formatDuration(startIso: String?, endIso: String?): String {
    if (startIso.isNullOrBlank()) return ""
    val start = runCatching { java.time.Instant.parse(startIso) }.getOrNull() ?: return ""
    if (endIso.isNullOrBlank()) return ""
    val end = runCatching { java.time.Instant.parse(endIso) }.getOrNull() ?: return ""
    val seconds = java.time.Duration.between(start, end).seconds
    return when {
        seconds < 0 -> ""
        seconds < 60 -> "${seconds}秒"
        seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
        seconds < 86400 -> "${seconds / 3600}小时${(seconds % 3600) / 60}分"
        else -> "${seconds / 86400}天${(seconds % 86400) / 3600}小时"
    }
}

/** 运行状态徽标：结论优先（success 绿 / failure 红 / cancelled 灰），进行中黄、排队蓝 */
@Composable
fun WorkflowStatusBadge(status: String, conclusion: String?) {
    val (text, color) = when {
        conclusion == "success" -> "成功" to Color(0xFF1F883D)
        conclusion == "failure" -> "失败" to Color(0xFFCF222E)
        conclusion == "cancelled" -> "已取消" to Color(0xFF6E7781)
        conclusion == "skipped" -> "已跳过" to Color(0xFF6E7781)
        conclusion == "timed_out" -> "超时" to Color(0xFFCF222E)
        conclusion == "action_required" -> "待处理" to Color(0xFFBF8700)
        status == "in_progress" -> "进行中" to Color(0xFFBF8700)
        status == "queued" -> "排队中" to Color(0xFF0969DA)
        else -> (conclusion ?: status) to Color(0xFF6E7781)
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
