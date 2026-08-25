package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.PullRequest
import com.github.releaseuploader.ui.viewmodel.PullsViewModel

/** 拉取请求列表页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullsScreen(
    owner: String,
    repo: String,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: PullsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadPulls(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拉取请求") },
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
                        Button(onClick = { viewModel.loadPulls(owner, repo) }) { Text("重试") }
                    }
                }
                uiState.pulls.isEmpty() -> {
                    Text("暂无未合并的拉取请求", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn {
                        items(uiState.pulls, key = { it.number }) { pull ->
                            PullRow(pull)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PullRow(pull: PullRequest) {
    ListItem(
        leadingContent = {
            Icon(
                Icons.Default.CallMerge,
                contentDescription = null,
                tint = Color(0xFF0969DA),
                modifier = Modifier.size(18.dp)
            )
        },
        headlineContent = {
            Text(
                text = pull.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text("#${pull.number} · ${pull.user?.login ?: "匿名"} · ${pull.createdAt.take(10)}")
        }
    )
}
