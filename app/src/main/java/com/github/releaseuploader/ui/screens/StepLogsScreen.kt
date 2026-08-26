package com.github.releaseuploader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.releaseuploader.ui.viewmodel.StepLogsViewModel

/** 步骤日志页：等宽小字号逐行显示，##[error] 标红 / ##[warning] 标黄 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepLogsScreen(
    owner: String,
    repo: String,
    jobId: Long,
    stepNumber: Int,
    stepName: String,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: StepLogsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) {
        viewModel.loadStepLogs(owner, repo, jobId, stepNumber, stepName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stepName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (uiState.lines.isNotEmpty()) {
                        TextButton(onClick = {
                            val clip = ClipData.newPlainText("step_logs", uiState.lines.joinToString("\n"))
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
                        Button(onClick = { viewModel.loadStepLogs(owner, repo, jobId, stepNumber, stepName) }) { Text("重试") }
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
                        itemsIndexed(uiState.lines) { index, rawLine ->
                            LogLine(rawLine)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(rawLine: String) {
    // 去掉 ##[error]/##[warning]/##[notice] 标记前缀，并着色
    val (text, color) = when {
        rawLine.startsWith("##[error]") -> rawLine.removePrefix("##[error]").trimStart() to Color(0xFFCF222E)
        rawLine.startsWith("##[warning]") -> rawLine.removePrefix("##[warning]").trimStart() to Color(0xFFBF8700)
        rawLine.startsWith("##[notice]") -> rawLine.removePrefix("##[notice]").trimStart() to Color(0xFF0969DA)
        rawLine.startsWith("##[") -> rawLine.substringAfter("]", rawLine) to Color(0xFF6E7781)
        else -> rawLine to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = color,
        maxLines = Int.MAX_VALUE,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 1.dp)
    )
}
