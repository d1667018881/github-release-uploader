package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.WorkflowRunJob
import com.github.releaseuploader.data.model.WorkflowRunStep
import com.github.releaseuploader.ui.viewmodel.JobDetailViewModel

/** 任务详情页：状态 + 耗时 + 步骤列表（✓绿/✗红/进行中），点击步骤看日志 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    owner: String,
    repo: String,
    jobId: Long,
    jobName: String,
    onStepClick: (Int, String) -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadJob(owner, repo, jobId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(jobName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        Button(onClick = { viewModel.loadJob(owner, repo, jobId) }) { Text("重试") }
                    }
                }
                uiState.job == null -> {
                    Text("任务不存在", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val job = uiState.job!!
                    LazyColumn {
                        item { JobSummaryCard(job) }
                        val steps = job.steps.orEmpty()
                        item {
                            Text(
                                text = "步骤（${steps.size}）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        if (steps.isEmpty()) {
                            item {
                                Text(
                                    "暂无步骤",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(steps, key = { it.number }) { step ->
                            StepRow(step) { onStepClick(step.number, step.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobSummaryCard(job: WorkflowRunJob) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = job.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                WorkflowStatusBadge(status = job.status, conclusion = job.conclusion)
            }
            val duration = formatDuration(job.startedAt, job.completedAt)
            if (duration.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "耗时 $duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "开始于 ${job.startedAt.take(16).replace('T', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 步骤行：状态图标（成功绿✓/失败红✗/进行中黄圈/跳过灰）+ 名称（等宽）+ 耗时 */
@Composable
private fun StepRow(step: WorkflowRunStep, onClick: () -> Unit) {
    val (icon, tint) = runStatusVisual(step.status, step.conclusion)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        },
        headlineContent = {
            Text(
                text = step.name,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val duration = formatDuration(step.startedAt, step.completedAt)
            if (duration.isNotEmpty()) {
                Text("耗时 $duration")
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}
