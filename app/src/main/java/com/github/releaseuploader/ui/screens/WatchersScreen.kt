package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.github.releaseuploader.data.model.User
import com.github.releaseuploader.ui.viewmodel.WatchersViewModel

/** 关注者列表页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchersScreen(
    owner: String,
    repo: String,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: WatchersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadWatchers(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关注者") },
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
                        Button(onClick = { viewModel.loadWatchers(owner, repo) }) { Text("重试") }
                    }
                }
                uiState.watchers.isEmpty() -> {
                    Text("暂无关注者", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn {
                        items(uiState.watchers, key = { it.login }) { user ->
                            WatcherRow(user)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatcherRow(user: User) {
    ListItem(
        leadingContent = {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        },
        headlineContent = { Text(user.login) }
    )
}
