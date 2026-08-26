package com.github.releaseuploader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.Artifact
import com.github.releaseuploader.data.model.WorkflowRun
import com.github.releaseuploader.data.model.WorkflowRunJob
import com.github.releaseuploader.ui.viewmodel.RunJobsViewModel

/** 运行详情页：run 概要（状态/耗时/触发）+ 产物（可下载）+ job 列表（点击看步骤） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunJobsScreen(
    owner: String,
    repo: String,
    runId: Long,
    onJobClick: (Long, String) -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: RunJobsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadRunDetail(owner, repo, runId)
    }
    // 下载结果提示
    LaunchedEffect(uiState.downloadMessage) {
        uiState.downloadMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.run?.let { "#${it.runNumber} ${it.name.orEmpty()}".trim() } ?: "运行详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
                        Button(onClick = { viewModel.loadRunDetail(owner, repo, runId) }) { Text("重试") }
                    }
                }
                else -> {
                    LazyColumn {
                        uiState.run?.let { run ->
                            item { RunSummaryCard(run) }
                        }
                        val artifacts = uiState.artifacts
                        if (artifacts.isNotEmpty()) {
                            item {
                                SectionTitle("产物（${artifacts.size}）")
                            }
                            items(artifacts, key = { it.id }) { artifact ->
                                ArtifactRow(
                                    artifact = artifact,
                                    isDownloading = uiState.isDownloading,
                                    onClick = { viewModel.downloadArtifact(artifact) }
                                )
                            }
                        }
                        item {
                            SectionTitle("任务（${uiState.jobs.size}）")
                        }
                        if (uiState.jobs.isEmpty() && !uiState.isLoading) {
                            item {
                                Text(
                                    "暂无任务",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(uiState.jobs, key = { it.id }) { job ->
                            JobRow(job) { onJobClick(job.id, job.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun RunSummaryCard(run: WorkflowRun) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${run.runNumber} ${run.name.orEmpty()}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                WorkflowStatusBadge(status = run.status, conclusion = run.conclusion)
            }
            val duration = formatDuration(run.createdAt, run.completedAt)
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
                text = buildString {
                    if (!run.event.isNullOrBlank()) append("触发：${run.event}")
                    run.actor?.login?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                    if (!run.headBranch.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("分支 ${run.headBranch}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArtifactRow(artifact: Artifact, isDownloading: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = !isDownloading, onClick = onClick),
        leadingContent = {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = {
            Text(artifact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                if (artifact.expired) "已过期"
                else "${formatSize(artifact.sizeInBytes)} · 过期 ${artifact.expiresAt.take(10)}"
            )
        },
        trailingContent = {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Download, contentDescription = "下载", tint = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

@Composable
private fun JobRow(job: WorkflowRunJob, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = job.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                WorkflowStatusBadge(status = job.status, conclusion = job.conclusion)
            }
        },
        supportingContent = {
            Text(
                text = buildString {
                    val duration = formatDuration(job.startedAt, job.completedAt)
                    if (duration.isNotEmpty()) append("耗时 $duration")
                    if (isNotEmpty()) append(" · ")
                    append("开始 ${job.startedAt.take(16).replace('T', ' ')}")
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
