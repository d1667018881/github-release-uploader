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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.ContentItem
import com.github.releaseuploader.service.UploadService
import com.github.releaseuploader.service.UploadState
import com.github.releaseuploader.ui.viewmodel.RepoDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    branch: String,
    onFileClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RepoDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by UploadService.uploadProgress.collectAsState()
    val context = LocalContext.current

    // 流程修正：先选文件 → 再建 Release → 成功后立即上传（避免取消选择留下空 Release）
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

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
            pendingUris = uris
            viewModel.createRelease(owner, repo) { uploadUrl ->
                if (uploadUrl.isNotBlank() && pendingUris.isNotEmpty()) {
                    startUpload(uploadUrl, pendingUris)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadContents(owner, repo)
    }

    // Release Dialog
    if (uiState.showReleaseDialog) {
        var tagInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.hideReleaseDialog() },
            title = { Text("Create Release") },
            text = {
                Column {
                    Text("Enter a tag name for the release:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Tag (e.g., v1.0.0)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        Text("Choose Files & Upload")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideReleaseDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(repo) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showReleaseDialog() }) {
                        Icon(Icons.Default.Upload, "Upload Release")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 应用内上传进度（订阅 UploadService.uploadProgress）
            if (uploadState.isUploading || uploadState.isComplete || uploadState.error != null) {
                UploadProgressBanner(uploadState)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.error != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadContents(owner, repo) }) {
                            Text("Retry")
                        }
                    }
                } else {
                    LazyColumn {
                        // Parent directory
                        if (uiState.currentPath.isNotEmpty()) {
                            item {
                                ListItem(
                                    headlineContent = { Text("..") },
                                    leadingContent = {
                                        Icon(Icons.Default.Folder, "Parent directory")
                                    },
                                    modifier = Modifier.clickable {
                                        val parentPath = uiState.currentPath.substringBeforeLast("/")
                                        viewModel.loadContents(owner, repo, parentPath)
                                    }
                                )
                            }
                        }
                        items(uiState.contents, key = { it.path }) { item ->
                            ContentItemRow(item = item, onClick = {
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
                        "Uploading ${state.currentFile} (${state.fileIndex}/${state.totalFiles})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.overallProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.isComplete -> {
                    Text("Upload complete!", color = MaterialTheme.colorScheme.primary)
                }
                state.error != null -> {
                    Text("Upload failed: ${state.error}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ContentItemRow(item: ContentItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = {
            if (item.type == "file") {
                Text("${item.size} bytes")
            }
        },
        leadingContent = {
            Icon(
                if (item.type == "dir") Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
