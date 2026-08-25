package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.WorkflowRunJob
import com.github.releaseuploader.ui.viewmodel.RunJobsViewModel

/** 运行记录的任务（job）列表页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunJobsScreen(
    owner: String,
    repo: String,
    runId: Long,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: RunJobsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadJobs(owner, repo, runId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行任务") },
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
                        Button(onClick = { viewModel.loadJobs(owner, repo, runId) }) { Text("重试") }
                    }
                }
                uiState.jobs.isEmpty() -> {
                    Text("暂无任务", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn {
                        items(uiState.jobs, key = { it.id }) { job ->
                            JobRow(job)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobRow(job: WorkflowRunJob) {
    ListItem(
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
                text = "开始于 ${job.startedAt.take(16).replace('T', ' ')}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
