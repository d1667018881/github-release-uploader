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
import com.github.releaseuploader.data.model.Contributor
import com.github.releaseuploader.ui.viewmodel.ContributorsViewModel

/** 贡献者列表页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributorsScreen(
    owner: String,
    repo: String,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: ContributorsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadContributors(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("贡献者") },
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
                        Button(onClick = { viewModel.loadContributors(owner, repo) }) { Text("重试") }
                    }
                }
                uiState.contributors.isEmpty() -> {
                    Text("暂无贡献者", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    LazyColumn {
                        items(uiState.contributors, key = { it.login }) { contributor ->
                            ContributorRow(contributor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorRow(contributor: Contributor) {
    ListItem(
        leadingContent = {
            AsyncImage(
                model = contributor.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        },
        headlineContent = { Text(contributor.login) },
        supportingContent = { Text("贡献 ${contributor.contributions} 次") }
    )
}
