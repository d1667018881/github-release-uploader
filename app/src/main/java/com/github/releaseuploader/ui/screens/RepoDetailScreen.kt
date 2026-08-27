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
import android.content.Intent
import android.net.Uri
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.TextView
import android.graphics.Typeface
import com.github.releaseuploader.data.model.Repo
import com.github.releaseuploader.ui.viewmodel.RepoDetailViewModel
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.CoreProps
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import org.commonmark.node.Heading

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
    /** README 里指向本仓库文件的链接（相对路径），App 内打开代码页 */
    onReadmeLinkClick: (String) -> Unit,
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
                        // ⚠️ openIssuesCount 是 GitHub 的 issue+PR 总数，文案如实标注
                        item { EntryItem(Icons.Default.Circle, Color(0xFF1F883D), "议题", "${repoData.openIssuesCount} 个（含拉取请求）", onClick = onIssuesClick) }
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
                                ReadmeSection(
                                    markdown = uiState.readme,
                                    owner = owner,
                                    repo = repo,
                                    branch = branch,
                                    onLinkClick = onReadmeLinkClick
                                )
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
private fun ReadmeSection(
    markdown: String,
    owner: String,
    repo: String,
    branch: String,
    onLinkClick: (String) -> Unit
) {
    val context = LocalContext.current
    // remember 捕获的是首次值，用 rememberUpdatedState 让 linkResolver 始终拿到最新回调
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    // 相对路径图片（![alt](docs/x.png)）预处理为 raw.githubusercontent 绝对 URL，供 Coil 加载
    val processedMarkdown = remember(markdown, owner, repo, branch) {
        if (markdown.isEmpty()) "" else resolveRelativeImageUrls(markdown, owner, repo, branch)
    }
    // 限制 Markdown 标题字号：默认 heading 会放大到 1.6 倍导致"字体太大"，
    // 自定义 SpanFactory 让 h1-h3 最大只放大 1.2/1.1/1.05 倍
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create())
            .usePlugin(CoilImagesPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(Heading::class.java) { _, props ->
                        val level = props.get(CoreProps.HEADING_LEVEL) ?: 1
                        val ratio = when (level) {
                            1 -> 1.2f
                            2 -> 1.1f
                            3 -> 1.05f
                            else -> 1.0f
                        }
                        arrayOf(RelativeSizeSpan(ratio), StyleSpan(Typeface.BOLD))
                    }
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    // README 链接：仓库内相对链接在 App 内打开（代码页），外部 http(s)/mailto:/tel: 交系统
                    builder.linkResolver { view, link ->
                        var clean = link.substringBefore('#').substringBefore('?')
                        // 非 http 协议（mailto:/tel: 等）交给系统处理
                        if (clean.startsWith("mailto:") || clean.startsWith("tel:")) {
                            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                            return@linkResolver
                        }
                        if (clean.startsWith("http://") || clean.startsWith("https://")) {
                            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                        } else {
                            // 仓库内相对路径归一化：去掉 ./ 和开头 /（README 里很常见的写法）
                            clean = clean.removePrefix("./").removePrefix("/")
                            if (clean.isNotBlank()) {
                                currentOnLinkClick(clean)
                            }
                        }
                    }
                }
            })
            .build()
    }
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
                    // 12sp 小字号（与功能入口文字相当或更小），1.3 倍行距。
                    // ⚠️ setTextSize(float) 默认单位是 sp（内部已应用 scaledDensity），
                    // 不能再手动乘 scaledDensity，否则字号会二次放大（曾致 README 字体偏大）。
                    textSize = 12f
                    setTextColor(textColor.toArgb())
                    setBackgroundColor(surfaceColor.toArgb())
                    setLineSpacing(0f, 1.3f)
                    setPadding(16, 0, 16, 24)
                }
            },
            update = { tv ->
                // O6：markdown 未变化时跳过重复 parse（README 长时收益明显）
                if (tv.tag != processedMarkdown) {
                    tv.tag = processedMarkdown
                    markwon.setMarkdown(tv, processedMarkdown)
                }
            }
        )
    }
}

/**
 * 把 Markdown 里的相对路径图片替换为 raw.githubusercontent 绝对 URL。
 * ⚠️ 绝对 URL（含 shields.io 动态徽章）必须原样保留 query，只有相对路径才剥 #/?。
 * 同时处理引用式图片定义（[ref]: docs/x.png），否则引用式写法加载失败。
 */
private fun resolveRelativeImageUrls(markdown: String, owner: String, repo: String, branch: String): String {
    val rawBase = "https://raw.githubusercontent.com/$owner/$repo/$branch/"
    // ① 引用式图片定义：`[ref]: path`（行首）
    val withRefs = Regex("^\\[([^\\]]+)\\]:\\s*(\\S+)", RegexOption.MULTILINE).replace(markdown) { m ->
        val name = m.groupValues[1]
        val url = m.groupValues[2]
        val resolved = when {
            url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:") || url.startsWith("//") -> url
            else -> rawBase + url.substringBefore('#').substringBefore('?').removePrefix("./").removePrefix("/")
        }
        "[$name]: $resolved"
    }
    // ② 内联图片：`![alt](path)`，绝对 URL 原样保留
    return Regex("!\\[([^]]*)\\]\\(([^)]+)\\)").replace(withRefs) { m ->
        val alt = m.groupValues[1]
        val raw = m.groupValues[2]
        val resolved = when {
            raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("data:") || raw.startsWith("//") -> raw
            else -> rawBase + raw.substringBefore('#').substringBefore('?').removePrefix("./").removePrefix("/")
        }
        "![$alt]($resolved)"
    }
}
