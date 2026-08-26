package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
private fun RunRow(run: WorkflowRun, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${run.runNumber} ${run.name ?: ""}".trim(),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                WorkflowStatusBadge(status = run.status, conclusion = run.conclusion)
            }
        },
        supportingContent = {
            Text(
                text = buildString {
                    if (!run.headBranch.isNullOrBlank()) append(run.headBranch)
                    if (!run.event.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("触发：${run.event}")
                    }
                    run.actor?.login?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                    append(" · ")
                    append(run.createdAt.take(16).replace('T', ' '))
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
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
