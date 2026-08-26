package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.ui.viewmodel.JobLogsViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

/** 任务日志查看页：等宽字体小字号逐行显示 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobLogsScreen(
    owner: String,
    repo: String,
    jobId: Long,
    jobName: String,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: JobLogsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadLogs(owner, repo, jobId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(jobName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (uiState.lines.isNotEmpty()) {
                        TextButton(onClick = {
                            val clip = ClipData.newPlainText("job_logs", uiState.lines.joinToString("\n"))
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                ?.setPrimaryClip(clip)
                            Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("复制")
                        }
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
                        Button(onClick = { viewModel.loadLogs(owner, repo, jobId) }) { Text("重试") }
                    }
                }
                uiState.lines.isEmpty() -> {
                    Text(
                        "暂无日志",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(uiState.lines) { index, line ->
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = Int.MAX_VALUE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
