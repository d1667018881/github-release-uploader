package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.Workflow
import com.github.releaseuploader.ui.viewmodel.ActionsViewModel

/** 操作（工作流）列表页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    owner: String,
    repo: String,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: ActionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadWorkflows(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("操作") },
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
                        Button(onClick = { viewModel.loadWorkflows(owner, repo) }) { Text("重试") }
                    }
                }
                uiState.workflows.isEmpty() -> {
                    Text("暂无工作流", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn {
                        items(uiState.workflows, key = { it.id }) { workflow ->
                            WorkflowRow(workflow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRow(workflow: Workflow) {
    ListItem(
        leadingContent = {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFFBF8700),
                modifier = Modifier.size(22.dp)
            )
        },
        headlineContent = {
            Text(
                text = workflow.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = if (workflow.state == "active") "已启用 · ${workflow.path}" else "已禁用 · ${workflow.path}",
                color = if (workflow.state == "active") Color(0xFF1F883D) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
