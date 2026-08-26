package com.github.releaseuploader.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.Release
import com.github.releaseuploader.service.UploadService
import com.github.releaseuploader.service.UploadState
import com.github.releaseuploader.ui.viewmodel.ReleasesViewModel

/** 发行版列表页：列表 + 新建发行版（选文件创建并上传附件） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(
    owner: String,
    repo: String,
    onReleaseClick: (Long) -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReleasesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by UploadService.uploadProgress.collectAsState()
    val context = LocalContext.current

    // 流程：先选文件 → 再建 Release → 成功后立即上传（避免取消选择留下空 Release）。
    // rememberSaveable 存 String 列表（Uri 非自动可保存），旋转屏幕不丢已选文件
    var pendingUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    // Android 13+ 通知权限（不授权也能上传，只是进度通知不显示）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    // 注意：局部函数必须在使用它的 lambda 之前声明（Kotlin 词法作用域规则）
    fun startUpload(uploadUrl: String, uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val intent = Intent(context, UploadService::class.java).apply {
            putStringArrayListExtra(UploadService.EXTRA_FILES, ArrayList(uris.map { it.toString() }))
            putExtra(UploadService.EXTRA_UPLOAD_URL, uploadUrl)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            // 持久化 URI 读权限（跨设备重启仍有效）；部分 provider 不支持会抛 SecurityException，
            // runCatching 降级为一次性权限（本次会话内仍可上传）
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            pendingUris = uris.map { it.toString() }
            viewModel.createRelease(owner, repo) { uploadUrl ->
                if (uploadUrl.isNotBlank() && pendingUris.isNotEmpty()) {
                    startUpload(uploadUrl, pendingUris.map { Uri.parse(it) })
                }
            }
        }
    }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        // 页面进入时重置非上传中的残留进度（避免看到上次的旧 "上传完成！"）
        if (!UploadService.uploadProgress.value.isUploading) {
            UploadService.resetState()
        }
        viewModel.loadReleases(owner, repo)
    }
    // 上传完成后刷新列表（能看到新创建的 Release）
    LaunchedEffect(uploadState.isComplete) {
        if (uploadState.isComplete) {
            viewModel.loadReleases(owner, repo)
        }
    }

    // 新建 Release 对话框：输入 tag → 选择文件 → 创建并上传
    if (uiState.showReleaseDialog) {
        var tagInput by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.hideReleaseDialog() },
            title = { Text("创建 Release") },
            text = {
                Column {
                    Text("输入 Release 标签名：")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("标签（如 v1.0.0）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.releaseError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.releaseError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setReleaseTag(tagInput)
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = tagInput.isNotBlank() && !uiState.isCreatingRelease
                ) {
                    if (uiState.isCreatingRelease) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("选择文件并上传")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideReleaseDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发行版") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showReleaseDialog() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新建发行版") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 应用内上传进度（订阅 UploadService.uploadProgress）
            if (uploadState.isUploading || uploadState.isComplete || uploadState.error != null) {
                UploadProgressBanner(uploadState)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    uiState.error != null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("错误：${uiState.error}", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadReleases(owner, repo) }) { Text("重试") }
                        }
                    }
                    uiState.releases.isEmpty() -> {
                        Text("暂无发行版", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        LazyColumn {
                            items(uiState.releases, key = { it.id }) { release ->
                                ReleaseItem(release) { onReleaseClick(release.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseItem(release: Release, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Label,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!release.name.isNullOrBlank() && release.name != release.tagName) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = release.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!release.body.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = release.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "发布于 ${release.createdAt.take(10)} · ${release.assets?.size ?: 0} 个附件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadProgressBanner(state: UploadState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when {
                state.isUploading -> {
                    Text(
                        "正在上传 ${state.currentFile}（${state.fileIndex}/${state.totalFiles}）",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.overallProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.isComplete -> {
                    Text("上传完成！", color = MaterialTheme.colorScheme.primary)
                }
                state.error != null -> {
                    Text("上传失败：${state.error}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
