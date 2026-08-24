package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.ui.viewmodel.CodeBrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeBrowserScreen(
    owner: String,
    repo: String,
    branch: String,
    path: String,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: CodeBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 限流登出时导航回登录页
    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLoggedOut()
        }
    }

    LaunchedEffect(path) {
        viewModel.loadFile(owner, repo, path)
    }

    // URL 每段单独 Uri.encode，避免 path/branch 含空格或特殊字符时 URL 损坏
    val encodedPath = path.split("/").joinToString("/") { Uri.encode(it) }
    val openInBrowser: () -> Unit = {
        val url = "https://github.com/$owner/$repo/blob/${Uri.encode(branch)}/$encodedPath"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = openInBrowser) {
                        Icon(Icons.Default.OpenInBrowser, "Open in browser")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                        if (uiState.error?.contains("too large") == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = openInBrowser) {
                                Text("Open in Browser")
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = uiState.content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
