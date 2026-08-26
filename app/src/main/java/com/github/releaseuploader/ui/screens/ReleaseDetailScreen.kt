package com.github.releaseuploader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.Release
import com.github.releaseuploader.data.model.ReleaseAsset
import com.github.releaseuploader.ui.viewmodel.ReleaseDetailViewModel

/** 发行版详情页：完整说明 + 附件列表（点击下载） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailScreen(
    owner: String,
    repo: String,
    releaseId: Long,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReleaseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadRelease(owner, repo, releaseId)
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
                title = { Text("发行版详情") },
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
                        Button(onClick = { viewModel.loadRelease(owner, repo, releaseId) }) { Text("重试") }
                    }
                }
                uiState.release == null -> {
                    Text("发行版不存在", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val release = uiState.release!!
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        item {
                            ReleaseDetailHeader(release)
                        }
                        val assets = release.assets.orEmpty()
                        if (assets.isNotEmpty()) {
                            item {
                                Text(
                                    text = "附件（${assets.size}）",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(assets, key = { it.id }) { asset ->
                                AssetRow(asset) {
                                    viewModel.downloadAsset(asset)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseDetailHeader(release: Release) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Label,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = release.tagName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        if (!release.name.isNullOrBlank() && release.name != release.tagName) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = release.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "发布于 ${release.createdAt.take(10)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!release.body.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = release.body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AssetRow(asset: ReleaseAsset, onDownload: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDownload)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatSize(asset.size)} · 下载 ${asset.downloadCount} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Download,
                contentDescription = "下载",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
