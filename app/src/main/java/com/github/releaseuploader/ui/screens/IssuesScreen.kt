package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.Issue
import com.github.releaseuploader.ui.viewmodel.IssuesViewModel

/** 议题列表页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(
    owner: String,
    repo: String,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: IssuesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadIssues(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("议题") },
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
                        Button(onClick = { viewModel.loadIssues(owner, repo) }) { Text("重试") }
                    }
                }
                uiState.issues.isEmpty() -> {
                    Text("暂无未解决的议题", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn {
                        items(uiState.issues, key = { it.number }) { issue ->
                            IssueRow(issue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: Issue) {
    ListItem(
        leadingContent = {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                tint = Color(0xFF1F883D),
                modifier = Modifier.size(16.dp)
            )
        },
        headlineContent = {
            Text(
                text = issue.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text("#${issue.number} · ${issue.user?.login ?: "匿名"} · ${issue.createdAt.take(10)}")
        }
    )
}
