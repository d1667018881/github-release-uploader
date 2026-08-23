package com.github.releaseuploader.ui.screens

import android.content.Intent
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.data.model.ContentItem
import com.github.releaseuploader.service.UploadService
import com.github.releaseuploader.ui.viewmodel.RepoDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    onFileClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RepoDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val uploadUrl = uiState.releaseTag.let {
                // Use the stored upload URL from create release
                ""
            }
            if (uploadUrl.isNotBlank()) {
                val intent = Intent(context, UploadService::class.java).apply {
                    putStringArrayListExtra(
                        UploadService.EXTRA_FILES,
                        ArrayList(uris.map { it.toString() })
                    )
                    putExtra(UploadService.EXTRA_OWNER, owner)
                    putExtra(UploadService.EXTRA_REPO, repo)
                    putExtra(UploadService.EXTRA_UPLOAD_URL, uploadUrl)
                }
                context.startService(intent)
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
                        viewModel.createRelease(owner, repo) { uploadUrl ->
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    enabled = tagInput.isNotBlank() && !uiState.isCreatingRelease
                ) {
                    if (uiState.isCreatingRelease) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Create & Upload")
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
