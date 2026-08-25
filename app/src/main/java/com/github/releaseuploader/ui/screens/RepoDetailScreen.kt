package com.github.releaseuploader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.TextView
import com.github.releaseuploader.data.model.Repo
import com.github.releaseuploader.ui.viewmodel.RepoDetailViewModel
import io.noties.markwon.Markwon

/** 仓库概览页：GitHub 官方 App 风格——仓库信息 + 功能入口（议题/PR/操作/发行版/贡献者/关注者/代码）+ README 渲染 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    branch: String,
    onCodeClick: () -> Unit,
    onIssuesClick: () -> Unit,
    onPullsClick: () -> Unit,
    onActionsClick: () -> Unit,
    onReleasesClick: () -> Unit,
    onContributorsClick: () -> Unit,
    onWatchersClick: () -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: RepoDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLoggedOut()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadDetail(owner, repo)
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.repo == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("错误：${uiState.error ?: "仓库加载失败"}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDetail(owner, repo) }) {
                            Text("重试")
                        }
                    }
                }
                else -> {
                    val repoData = uiState.repo!!
                    LazyColumn {
                        item { RepoHeader(repoData) }
                        item {
                            // 标星操作
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.toggleStar(owner, repo) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        if (uiState.isStarred) Icons.Default.Star else Icons.Outlined.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (uiState.isStarred) Color(0xFFE3B341) else Color.Unspecified
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (uiState.isStarred) "已标星" else "标星")
                                }
                            }
                        }
                        // 功能入口（App 内页面导航，不跳浏览器）
                        item { EntryItem(Icons.Default.Circle, Color(0xFF1F883D), "议题", "${repoData.openIssuesCount} 个未解决", onClick = onIssuesClick) }
                        item { EntryItem(Icons.Default.CallMerge, Color(0xFF0969DA), "拉取请求", onClick = onPullsClick) }
                        item { EntryItem(Icons.Default.PlayArrow, Color(0xFFBF8700), "操作", onClick = onActionsClick) }
                        item {
                            val latest = uiState.releases.firstOrNull()
                            EntryItem(
                                Icons.Default.Label,
                                Color(0xFF24292F),
                                "发行版",
                                subtitle = if (uiState.releases.isEmpty()) null
                                    else "${latest?.tagName} · 共 ${uiState.releases.size} 个",
                                onClick = onReleasesClick
                            )
                        }
                        item {
                            EntryItem(
                                Icons.Default.People,
                                Color(0xFFBF3981),
                                "贡献者",
                                subtitle = if (uiState.contributors.isEmpty()) null else "${uiState.contributors.size} 人",
                                onClick = onContributorsClick
                            )
                        }
                        item { EntryItem(Icons.Default.Visibility, Color(0xFFBF8700), "关注者", "${repoData.watchersCount} 人", onClick = onWatchersClick) }
                        item { EntryItem(Icons.Default.Folder, Color(0xFF57606A), "代码", "浏览仓库文件", onClick = onCodeClick) }
                        // README
                        if (uiState.readme.isNotBlank()) {
                            item {
                                ReadmeSection(uiState.readme)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoHeader(repo: Repo) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = repo.fullName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (!repo.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = repo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (repo.isPrivate) {
                Text("🔒 私人", style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Star, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("${repo.stargazersCount} 个星标", style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CallSplit, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("${repo.forksCount} 个复刻", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EntryItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ReadmeSection(markdown: String) {
    val context = LocalContext.current
    val markwon = remember { Markwon.builder(context).build() }
    // 适配深浅色主题
    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "README.md",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    // 13sp 适中字号，1.3 倍行距提升可读性
                    textSize = 13f * ctx.resources.displayMetrics.scaledDensity
                    setTextColor(textColor.toArgb())
                    setBackgroundColor(surfaceColor.toArgb())
                    setLineSpacing(0f, 1.3f)
                    setPadding(16, 0, 16, 24)
                }
            },
            update = { tv ->
                markwon.setMarkdown(tv, markdown)
            }
        )
    }
}
