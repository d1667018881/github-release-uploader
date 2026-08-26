package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.ContentItem
import com.github.releaseuploader.ui.viewmodel.RepoFilesViewModel

/** 仓库文件浏览页：目录导航 + 代码查看 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoFilesScreen(
    owner: String,
    repo: String,
    branch: String,
    onFileClick: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: RepoFilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 限流登出时导航回登录页
    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLoggedOut()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadContents(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(repo) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("错误：${uiState.error}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadContents(owner, repo, uiState.currentPath) }) {
                            Text("重试")
                        }
                    }
                }
                else -> {
                    LazyColumn {
                        // 当前路径显示
                        item {
                            Text(
                                text = if (uiState.currentPath.isEmpty()) "根目录" else uiState.currentPath,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        // 上级目录
                        if (uiState.currentPath.isNotEmpty()) {
                            item {
                                ListItem(
                                    headlineContent = { Text("..") },
                                    leadingContent = {
                                        Icon(Icons.Default.Folder, "上级目录")
                                    },
                                    modifier = Modifier.clickable {
                                        val parentPath = uiState.currentPath.substringBeforeLast("/")
                                        viewModel.loadContents(owner, repo, parentPath)
                                    }
                                )
                            }
                        }
                        items(uiState.contents, key = { it.path }) { item ->
                            FileItemRow(item = item, onClick = {
                                if (item.type == "dir") {
                                    viewModel.loadContents(owner, repo, item.path)
                                } else {
                                    onFileClick(item.path)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItemRow(item: ContentItem, onClick: () -> Unit) {
    val isDir = item.type == "dir"
    ListItem(
        headlineContent = {
            Text(
                text = item.name,
                fontWeight = if (isDir) FontWeight.Medium else FontWeight.Normal
            )
        },
        leadingContent = {
            Icon(
                if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = if (isDir) "目录" else "文件",
                tint = if (isDir) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
