# AI 接手工作规范 (AI Handoff Protocol)

> 本文档专为 AI 助手（Claude、GPT、MClaw 等）设计，确保任何 AI 在拿到仓库地址后可以直接开始工作。

---

## 一、项目信息速查

```
仓库: d1667018881/github-release-uploader
架构: MVVM + Repository
语言: Kotlin 2.0
构建: Gradle 8.7 + AGP 8.5.2 + JDK 17
UI: Jetpack Compose + Material3
DI: Hilt 2.51.1
CI: GitHub Actions (.github/workflows/android-build.yml)
```

---

## 二、接手项目第一步（必须执行）

### 步骤 1：获取最新状态

```bash
TOKEN="ghp_xxx"
REPO="d1667018881/github-release-uploader"

# 获取最新构建状态
curl -s "https://api.github.com/repos/${REPO}/actions/runs?per_page=1" \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" | python3 -c "
import sys,json
r=json.load(sys.stdin)['workflow_runs'][0]
print(f'Run: {r[\"id\"]} | Status: {r[\"status\"]} | Conclusion: {r.get(\"conclusion\",\"?\")} | Branch: {r[\"head_branch\"]}')
"

# 获取最新 Release
curl -s "https://api.github.com/repos/${REPO}/releases?per_page=1" \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" | python3 -c "
import sys,json
r=json.load(sys.stdin)
if r: print(f'Latest: {r[0][\"tag_name\"]} | {r[0][\"name\"]}')
else: print('No releases')
"
```

### 步骤 2：读取关键文件（按优先级）

1. `README.md` — 完整项目文档
2. `AI_HANDOFF.md` — 本文档
3. `.github/workflows/android-build.yml` — CI/CD 配置
4. `app/build.gradle.kts` — 依赖与版本
5. `settings.gradle.kts` — 项目设置
6. 本文档第八节「已知坑与易错点」— 接手必读，避免踩雷

---

## 三、文件修改标准流程

### 通过 GitHub API 修改（推荐，无需 Git 环境）

```bash
# 1. 读取文件获取 SHA
SHA=$(curl -s "https://api.github.com/repos/${REPO}/contents/{filepath}" \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" | python3 -c "import sys,json; print(json.load(sys.stdin)['sha'])")

# 2. Base64 编码新内容
B64=$(cat {local_file} | base64 -w0)

# 3. 提交更新
curl -s -X PUT "https://api.github.com/repos/${REPO}/contents/{filepath}" \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -d "{\"message\":\"{commit message}\",\"content\":\"$B64\",\"sha\":\"$SHA\"}"
```

### 通过 Git 推送（需要本地 Git 环境）

```bash
git clone https://{username}:{token}@github.com/d1667018881/github-release-uploader.git
# 修改文件...
git add .
git commit -m "描述修改内容"
git push origin main
```

### 多文件一次性提交（推荐，避免多次 API 调用踩 SHA）

当需要一次改多个文件（如联动修改 ViewModel + Screen + Repository）时，**不要**用上面单个 contents API 逐个 PUT（每个文件单独 commit，且容易丢 SHA）。改用 **Git Data API 的 base_tree 增量提交**，把多个文件合成一个 commit：

```bash
# 1. 取当前分支最新 commit 的 tree SHA
HEAD_SHA=$(curl -s "https://api.github.com/repos/${REPO}/git/refs/heads/main" \
  -H "Authorization: token $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['object']['sha'])")
BASE_TREE=$(curl -s "https://api.github.com/repos/${REPO}/git/commits/${HEAD_SHA}" \
  -H "Authorization: token $TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['tree']['sha'])")

# 2. 每个文件生成一个 tree item（用 git hash-object 或本地 blob），带 base_tree 建新 tree
# 3. 用新 tree 建 commit，parent 指向 HEAD_SHA
# 4. PATCH /repos/${REPO}/git/refs/heads/main 更新分支指针
```

要点：`base_tree` 只传**改动文件**的 blob，未改文件自动继承 base_tree，最终一次性更新分支，触发一次 CI。

---

## 四、构建监控与自动修复

### 监控构建状态

```bash
sleep 300  # 首次等 5 分钟再查（Android 构建需下载依赖较慢），之后每 30s 轮询一次

for i in $(seq 1 60); do
    sleep 30
    RESULT=$(curl -s "https://api.github.com/repos/${REPO}/actions/runs?per_page=1" \
      -H "Authorization: token $TOKEN" \
      -H "Accept: application/vnd.github+json")
    STATUS=$(echo "$RESULT" | python3 -c "import sys,json; r=json.load(sys.stdin)['workflow_runs'][0]; print(r['status'])")
    CONCLUSION=$(echo "$RESULT" | python3 -c "import sys,json; r=json.load(sys.stdin)['workflow_runs'][0]; print(r.get('conclusion','in_progress'))")
    if [ "$STATUS" = "completed" ]; then break; fi
done
```

### 下载失败日志

```bash
RUN_ID="xxx"
curl -sL -o /tmp/logs.zip \
  -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/${REPO}/actions/runs/${RUN_ID}/logs"

python3 -c "
import zipfile
with zipfile.ZipFile('/tmp/logs.zip') as z:
    z.extractall('/tmp/logs')
"

# 查看构建步骤日志
cat /tmp/logs/build/6_Build\ Release\ APK.txt
```

### 自动修复决策树

```
编译失败
├── Unresolved reference → 检查 import 或 API 名称
├── Accidental override → 重命名冲突变量
├── Incompatible version → 修改 build.gradle.kts 版本号
├── Could not find dependency → 检查 Maven 仓库配置
├── settings.gradle.kts 错误 → 检查 Gradle 版本兼容性
└── 其他 → 搜索错误信息，参考 Stack Overflow / GitHub Issues
```

### 修复后重试上限

**最多 5 次自动修复。** 超过 5 次仍失败，停止并报告最后的错误日志。

---

## 五、关键文件修改指南

### 添加新依赖

修改 `app/build.gradle.kts`，在 `dependencies` 块中添加：

```kotlin
implementation("group:artifact:version")
```

### 添加新页面

1. 创建 Screen 文件：`ui/screens/NewScreen.kt`
2. 创建 ViewModel：`ui/viewmodel/NewViewModel.kt`
3. 在 `NavGraph.kt` 中添加路由
4. 在 `Screen` sealed class 中添加路由定义

### 添加新 API 接口

1. 在 `GitHubApi.kt` 中添加 Retrofit 接口方法
2. 在 `GitHubRepository.kt` 中添加封装方法
3. 在 ViewModel 中调用 Repository 方法

### 修改 CI/CD 工作流

修改 `.github/workflows/android-build.yml`，注意：
- 不要删除 `permissions: contents: write`
- 不要修改签名配置
- 版本号通过 `-PversionCode` 传递

---

## 六、版本兼容性矩阵

### 已验证兼容的组合

| Gradle | AGP | Kotlin | KSP | JDK | 状态 |
|--------|-----|--------|-----|-----|------|
| 8.7 | 8.5.2 | 2.0.0 | 2.0.0-1.0.22 | 17 | ✅ 已验证 |

### 升级指南

| 目标 | 需要同步修改 |
|------|-------------|
| 升级 Gradle | `gradle-wrapper.properties`、`settings.gradle.kts` |
| 升级 AGP | `build.gradle.kts`（根）、`app/build.gradle.kts` |
| 升级 Kotlin | `build.gradle.kts`（根）、`app/build.gradle.kts` |
| 升级 KSP | `build.gradle.kts`（根），版本必须与 Kotlin 匹配 |
| 升级 Hilt | `app/build.gradle.kts` |

---

## 七、常见场景操作手册

### 场景 A：用户要求修改某个页面

1. 读取对应的 Screen 文件
2. 修改 Compose UI 代码
3. 如需修改数据逻辑，同步修改 ViewModel 和 Repository
4. 提交推送，等待构建

### 场景 B：用户要求添加新功能

1. 规划需要的文件（Screen、ViewModel、Model、API 接口等）
2. 按顺序创建文件
3. 在 `NavGraph.kt` 中添加路由
4. 提交推送，等待构建

### 场景 C：构建失败需要修复

1. 下载失败日志
2. 定位错误文件和行号
3. 根据错误类型选择修复策略
4. 修改文件，提交推送
5. 监控新构建，最多重试 5 次

### 场景 D：用户要求查看构建产物

1. 获取最新 Release：
   ```bash
   curl -s "https://api.github.com/repos/${REPO}/releases?per_page=1" \
     -H "Authorization: token $TOKEN"
   ```
2. 或获取最新构建产物：
   ```bash
   curl -s "https://api.github.com/repos/${REPO}/actions/artifacts" \
     -H "Authorization: token $TOKEN"
   ```

---

## 八、注意事项与禁忌

### ✅ 可以做

- 修改 `app/build.gradle.kts` 中的依赖版本
- 修改任何 `.kt` 源码文件
- 修改 `.github/workflows/android-build.yml`
- 修改 `settings.gradle.kts`
- 添加新的资源文件
- 使用 GitHub API 直接推送文件

### ❌ 禁止做

- 删除 `permissions: contents: write` 权限
- 修改 Release 的签名流程（固定 keystore 从 GitHub Secrets 读取，**不要改回 debug 签名**）
- 硬编码 Token 或密钥到代码中
- 把真实 Token 写进 AI_HANDOFF.md / README / commit message / issue（占位用 `ghp_xxx` 即可）
- 修改 `gradle-wrapper.properties` 中的 distributionUrl 为不兼容版本
- 在未验证兼容性的情况下大幅度升级 Gradle/AGP/Kotlin

### ⚠️ 已知坑与易错点（接手必读）

#### 上传链路（最容易踩的坑）

- **上传协议是 raw body，不是 multipart**：`GitHubApi.uploadReleaseAsset` 用 `@Body RequestBody` POST 到 `uploads.github.com`，不是 `@Multipart`。
- **upload_url 必须替换模板**：`createRelease` 返回的 `upload_url` 形如 `.../assets{?name,label}`，必须经 `GitHubRepository.resolveUploadUrl()` 替换为 `?name=<urlencoded fileName>` 再上传，否则 422。
- **uploadUrl 的流向**：`createRelease` 成功后写入 `RepoDetailUiState.uploadUrl`，文件选择器回调从 `viewModel.uiState.value.uploadUrl` 读取。不要在 Screen 里重新拼 URL。
- **上传进度是真实的**：`ProgressRequestBody` 用 `openFileDescriptor().statSize` 取长度、按字节块回调 `onProgress`。不要改回 `stream.available()`（对 content:// 流不准确，进度条会错）。
- **前台服务必须 `ContextCompat.startForegroundService()`**，不能直接 `startService()`（Android 8+）。

#### 安全红线

- Release 用 **debug 签名**（keystore 公开），任何人可伪造升级包，仅适合个人自用，勿对外分发。
- `HttpLoggingInterceptor` 已设为 debug=BASIC / release=NONE，**不要改回 `BODY`**（会把 Authorization header 的 Token 打进 logcat）。
- `android:allowBackup="false"` 且已移除 `usesCleartextTraffic`，勿改回，否则 Token 有泄露风险。

#### 其他已知限制

- `CodeBrowserScreen` 的「Open in browser」已改为经导航参数传入 `Repo.defaultBranch`（不再硬编码 `blob/main/`）。
- 限流处理已收敛：`RateLimitInterceptor` 只检测 403 限流（`X-RateLimit-Remaining=0`），登出动作统一由 `SessionManager` 负责（清 Token + 清缓存 + 广播登出事件），UI 层只订阅 `SessionManager.loggedOut`（SharedFlow 事件型，不重放）。
- Android 13+ 的 `POST_NOTIFICATIONS` 已在 `RepoDetailScreen` 启动上传前运行时申请（未授权也能上传，仅通知不显示）。
- 上传进度已接通应用内 UI（`RepoDetailScreen` 订阅 `UploadService.uploadProgress` 展示进度条）。
- `GitHubRepository` 有轻量 LRU 内存缓存（目录/文件内容），登出时由 `SessionManager` 调 `clearCache()` 清理。

---

## 九、审查修复记录（2026-08-24，两份外部 AI 审查报告）

> 两份独立 AI 审查报告（代码优化审查 / 评审任务清单）共 **21 条独立告警**：
> **16 条已修复，5 条评估后不修改**（理由见下，接手时不要无故回退）。
> 涉及 15 个文件：新增 2 个（`ApiException.kt`、`SessionManager.kt`），修改 13 个。

### 已修复（16 条）

| 文件 | 修复内容 | 对应告警 |
|------|---------|---------|
| `network/ApiException.kt`（新） | 携带 HTTP code 的 IOException，供重试策略区分 4xx/5xx | 重试不区分错误类型 |
| `data/local/SessionManager.kt`（新） | 收敛「限流→登出」：清 Token + 清缓存 + SharedFlow 广播登出（事件型不重放，避免 StateFlow 重放误登出） | 限流逻辑分散 + 状态重放脆弱 |
| `data/repository/GitHubRepository.kt` | 抽 `safeApiCall<T>`：CancellationException 透传（不吞取消）+ 空 body 防护（消除 `body()!!` NPE）；重试仅网络 IOException/5xx，4xx 直接失败；contents/file LRU 内存缓存 + `clearCache()` | 吞取消异常 / NPE / 重试粗糙 / 样板代码 / 零缓存 |
| `ui/viewmodel/LoginViewModel.kt` | 改为订阅 `SessionManager.loggedOut`，不再各自 collect 限流状态 | 限流逻辑分散 |
| `ui/viewmodel/RepoListViewModel.kt` | 订阅 `SessionManager.loggedOut`；`loadMore` 用 `update{}` 原子读改写杜绝竞态；`30` 改 `Constants.PER_PAGE` | 分页竞态 / 硬编码 30 |
| `service/UploadService.kt` | `OpenableColumns.DISPLAY_NAME` 查真实文件名（SAF URI 的 lastPathSegment 不可靠）；完成/失败后 `delay(1500)` 再 `stopForeground+stopSelf`（不常驻）；进度 ≥1% 或 ≥250ms 节流；新上传前 `resetState()` | 文件名错误 / 服务不退出 / 通知刷爆 / 死代码残留 |
| `network/ProgressRequestBody.kt` | 缓冲 8KB→64KB，减少回调频率 | 通知无节流 |
| `ui/viewmodel/CodeBrowserViewModel.kt` | 网络请求 + Base64 解码整体 `withContext(Dispatchers.IO)`；`String(bytes, Charsets.UTF_8)` 显式 UTF-8 | 主线程解码 ANR / 乱码 |
| `ui/navigation/NavGraph.kt` | `CodeBrowser` 路由 `Uri.encode(path)`（子目录含 `/` 的文件可正常导航）；`RepoDetail`/`CodeBrowser` 路由增加 `{branch}` 参数 | 子目录文件打不开 / blob/main 硬编码 |
| `ui/screens/RepoListScreen.kt` | `onRepoClick` 增加第三参 `repo.defaultBranch` | blob/main 硬编码 |
| `ui/screens/CodeBrowserScreen.kt` | 「Open in browser」用 `branch` 参数拼 URL，不再硬编码 `blob/main/` | blob/main 硬编码 |
| `ui/screens/RepoDetailScreen.kt` | 流程改为**先选文件 → 建 Release → 立即上传**（取消选择不产生空 Release）；Android 13+ 上传前申请 `POST_NOTIFICATIONS`；订阅 `UploadService.uploadProgress` 展示应用内进度条 | 流程反了 / 通知权限未申请 / 进度死代码 |
| `network/AuthInterceptor.kt` | `Authorization: token` → `Bearer`（兼容 fine-grained PAT） | PAT 兼容性 |
| `utils/Constants.kt` | 新增 `MAX_CONTENTS_CACHE_SIZE=50`、`MAX_FILE_CACHE_SIZE=10` | 缓存支持 |
| `gradle.properties` | 新增 `org.gradle.caching=true`、`org.gradle.parallel=true` | 构建加速 |
| `README.md` / `AI_HANDOFF.md` | 上传流程描述更新；已知坑清单标记已修复项 | 文档对齐 |

### 评估后不修改（5 条，接手时遵守理由，勿强行回退/强行实施）

| 告警 | 不修改理由 |
|------|-----------|
| targetSdk/compileSdk 升 35 | AGP 8.5.2 最高支持 compileSdk 34，升 35 必须同步升 AGP（需 8.6+），未经验证矩阵、CI 有失败风险；项目走 GitHub Release 自分发不上 Play Store，无合规压力。**如要升，必须单独验证并更新第六节兼容矩阵** |
| release 换正式签名 | 原评估：debug 签名是有意为之、无需改；**已于 2026-08-25 落地固定 keystore**（存 GitHub Secrets，CI 解码签名），保证 CI 构建签名一致、可覆盖安装（commit：签名修复） |
| 开启 R8 混淆（isMinifyEnabled） | 需补充 Retrofit/Gson keep 规则并回归测试，收益（体积）低风险高；APK 约 12MB 可接受 |
| 依赖升级（BOM 2024.06 / AGP 8.5.2 / Kotlin 2.0.0） | 未验证兼容矩阵；升级必须走第六节流程先验证 |
| material-icons-extended / security-crypto | 审查报告原文建议「保留即可 / 知悉风险」；icons 依赖实际使用中，移除需替换图标集 |

### 关键设计决策（后续接手勿破坏）

- **缓存范围**：只缓存静态数据（目录列表、文件内容）；仓库列表是动态数据（星标/更新时间会变），不做缓存避免脏数据。
- **上传流程**：`createRelease` 成功回调拿到 `uploadUrl` 后立即启动 `UploadService`；`uploadUrl` 同时存入 `RepoDetailUiState.uploadUrl`。
- **限流链路**：`RateLimitInterceptor`（403+`X-RateLimit-Remaining=0` 检测）→ `SessionManager`（清 Token+清缓存+广播）→ ViewModel（仅 UI 响应）。登录成功时 `RateLimitInterceptor.reset()` 复位。
- **进度节流**：阈值 ≥1% 增量或 ≥250ms 间隔；缓冲 64KB。不要改回 8KB 或每块刷新。
- **导航编码**：`CodeBrowser` 的 path 参数必须 `Uri.encode()` 后拼接（Navigation 匹配后自动解码），否则子目录文件无法打开。

### 后续自查修复（2026-08-24 追加，commit 6a654b4）

- **Release 创建失败错误可见**：`RepoDetailUiState` 新增 `releaseError` 字段，`createRelease` 失败时在 Dialog 内显示错误（此前失败只写 `error`，被 Dialog 遮住看不到）。
- **终态通知可清除**：`UploadService` 的完成/失败通知改为非 `ongoing`（`setAutoCancel(true)`），`stopForeground(STOP_FOREGROUND_DETACH)` 保留通知在通知栏、用户可手动清除（此前 `STOP_FOREGROUND_REMOVE` 会直接移除，用户看不到结果）。
- **UploadState 顶层化**：`UploadState` 从 companion object 移至文件顶层 data class（companion 嵌套类类型引用在 K2 下解析不稳定，见 commit a5449c0）。

### 第二轮自查修复（2026-08-24 追加，commit 36278bf）

- **缓存并发锁**：`GitHubRepository` 缓存读写统一加 `synchronized(cacheLock)`。原因：`SessionManager` 登出时在 IO 线程调 `clearCache()`，而 `getContents`/`getFileContent` 可能在主线程写缓存，`LinkedHashMap` 非线程安全会 ConcurrentModificationException。
- **上传防重入**：`UploadService` 新增 `@Volatile isUploading`，重复触发上传时忽略新请求（此前连续触发会启动两个并发协程互相覆盖进度）；`ACTION_STOP` 分支也复位标志。
- **branch 路由编码**：`NavGraph` 的 `branch` 参数 `Uri.encode()`（GitHub 分支名可含 `/` 如 `release/v1.0`，不编码会路由段数不匹配）。

### 已知可选优化（未改，接手时知悉）

- `GitHubRepository` 缓存用 `LinkedHashMap`（非线程安全）：当前 `contentsCache` 仅在主线程、`fileCache` 仅在 IO 线程访问，无实际并发；若未来多线程调用需换 `ConcurrentHashMap` 或加锁。
- `SessionManager` 持有常驻 `CoroutineScope(IO)` 永不 cancel（单例生命周期=进程，可接受）。
- `UploadService.uploadProgress` 为 companion 全局 StateFlow，上传完成后 `isComplete` 会残留（重进页面可能看到旧「Upload complete!」），已在 `RepoDetailScreen` 进入页面时非上传中 `resetState()`（commit c37b02）。

### 智谱审查处理（2026-08-24 追加，commit c37b02 + 6ae176）

> 智谱审查共 30 条告警：**21 条已修复，7 条评估不修改，2 条此前已修**（#1 branch 编码、#2 缓存锁）。

**P0（6 条，全修）**
- #1 branch 含 `/` 路由崩溃 → 已修（commit 36278bf，`Uri.encode(branch)`，与智谱推荐的 query 参数方案等效）
- #2 缓存线程安全竞态 → 已修（commit 36278bf，`synchronized(cacheLock)`）
- #3 `openInputStream` 为 null 静默发空 body → 改抛 `IOException`
- #4 `contentLength()` 多次开 fd → `by lazy` 缓存长度
- #5 `ACTION_STOP` 不取消协程、无 UI 入口 → `serviceScope.cancel()` + 通知栏 Cancel action
- #6 RepoDetail/CodeBrowser 不响应限流登出 → 两个 ViewModel 订阅 `SessionManager.loggedOut`，Screen `LaunchedEffect` 触发，NavGraph `popUpTo(0)` 清栈回登录页

**P1（5 条，全修）**
- #7 createRelease/uploadAsset 未复用 safeApiCall → 已复用（⚠️ 注意：safeApiCall 签名 `(retryable, call)`，call 必须是最后一个参数，否则 trailing lambda 绑定错误，见 commit 6ae176）
- #8 GET 无重试 → `safeApiCall(retryable = true)`，网络错误/5xx 自动重试一次
- #9 contentLength -1 无进度 → 评估不修：-1 仅 statSize 失败时出现（极罕见），indeterminate 需全链路特判收益极低
- #10 Open in browser URL 未编码 → path/branch 每段 `Uri.encode()`
- #11 缓存回退无提示 → 加 `Log.w`

**P2（6 条，修 4 不修 2）**
- #12 零测试覆盖 → 不修：CI 无 test 任务，补了没人跑；建议作为独立工作项
- #13 依赖无 Version Catalog → 不修：单模块小项目，收益低迁移有风险
- #14 UI 字符串硬编码 → 不修：个人项目无 i18n 需求
- #15 reset 只在登录成功调 → `forceLogout()` 内补 `rateLimitInterceptor.reset()`
- #16 RepoItem/ContentItemRow public → 改 `private`
- #17 uploadProgress 重进残留 → `RepoDetailScreen` 进入时非上传中 `resetState()`

**P3（4 条，全不修）**
- #18 R8 开启 → 需 keep 规则+回归测试，收益低风险高
- #19 configuration-cache → AGP/KSP 兼容性未验证；`kotlin.incremental` 默认已开启
- #20 icons-extended → 用到的图标多不在 core，删了会编译失败
- #21 --no-daemon → CI 每次全新 runner 无 daemon 收益

**P4（9 条，修 8 不修 1）**
- #22 未用颜色 → 删 Purple80/PurpleGrey80/Pink80
- #23 浅色主题模板色 → 换 GitHub 浅色配色（#0969DA/#1A7F37/#CF222E）
- #24 perPage 硬编码 30 → 改 `Constants.PER_PAGE`
- #25 logout 竞态 → `SessionManager.loggedOut` 改 `SharedFlow<LogoutReason>`（RATE_LIMIT/MANUAL），手动登出不显示限流错误
- #26 tagInput → `rememberSaveable`
- #27 pendingUris → `rememberSaveable`（存 `List<String>`，旋转不丢文件）
- #28 queryDisplayName 吞异常 → 加 `Log.w`
- #29 缺 dataExtractionRules → 新增 `res/xml/data_extraction_rules.xml` 排除加密 Token 文件
- #30 snapshotFlow 缺 distinctUntilChanged → 已加

### 智谱 Round 2 复审处理（2026-08-24 追加，commit d4383a8）

> 二次复审 6 条：N1-N5 已修，N6 信息性无需改。

- **N1(P0) 上传取消竞态链**：`UploadService` 改 Job 引用方案——`ACTION_STOP` 只 `uploadJob?.cancel()` 不 `serviceScope.cancel()`（scope 取消后无法再 launch，取消后立即重传会静默卡死）；`catch (CancellationException)` 视作正常取消（显示 "Upload cancelled" 并 `throw e` 保持结构化并发，不再误报失败）；`finally` 包 `withContext(NonCancellable)` 保证取消后清理（delay/stopForeground/stopSelf/isUploading 复位）仍执行；取消不再残留失败通知。
- **N2(P1)**：`SessionManager._loggedOut` 加 `extraBufferCapacity = 4`，订阅者忙时 tryEmit 不丢事件。
- **N3(P2)**：删除 `LoginViewModel.logout()` 死代码（无调用方，实际登出走 RepoListViewModel）。
- **N4(P2)**：`RepoListScreen` 的 snapshotFlow 改发射 `lastVisibleIndex`（Int）——`LazyListLayoutInfo` 无 `equals()`，原 `distinctUntilChanged` 对对象无效。
- **N5(P1)**：`RepoDetailScreen` 文件选择回调加 `takePersistableUriPermission`（`runCatching` 包裹，provider 不支持时降级一次性权限），URI 读权限跨设备重启有效。
- **N6(信息)**：`data_extraction_rules.xml` 在 `allowBackup=false` 下不生效（死配置），保留作为未来开启备份的保险，不改。

### 智谱 Round 3 复审处理（2026-08-25 追加，commit fc0f3f4）

> 三次复审 3 条：R3-1/R3-2 已修，R3-3 附注无需改。Round 2 的 N1-N5 全部验证通过。

- **R3-1(P2) 取消误报完成**：上传循环内 `if (!isActive) break` 改为 `ensureActive()`——取消落在文件边界非挂起间隙时，break 会静默落到 "Upload complete!" 分支（部分上传显示为完成）；改为抛 `CancellationException` 走取消收尾（与 N1 的 catch 协同）。
- **R3-2a(P3)**：`ACTION_STOP` 的 else 分支（`uploadJob == null` 或已 completed）兜底清理：`isUploading=false` + `NotificationManager.cancel(NOTIFICATION_ID)` 清残留终态通知 + stopForeground/stopSelf（修"终态通知 Cancel 按钮点击无效"）。
- **R3-2b(P3)**：`createNotification` 仅 `ongoing=true`（上传中）时加 Cancel 按钮，终态通知无取消入口（语义更干净）。
- **R3-3(附注)**：N5 威胁模型修正（设备重启后 pendingUris 与前台服务同归于尽，无旧 URI 上传场景；持久化授权占系统配额，超限 runCatching 优雅降级）——无需改代码，保留现状。

### 应用文本中文化（2026-08-25 追加，commit e0f2700）

- 全部 Screen 的 UI 文本（按钮/标题/提示/进度/错误）改为中文：登录页、仓库列表、仓库详情（含上传 Dialog）、代码浏览。
- 通知文案（标题/开始/进行/完成/失败/取消）改为中文。
- Repository/ViewModel 的错误消息改为中文前缀（"HTTP 请求失败（code）：..."、"文件过大…请在 GitHub 网页端查看" 等）；"API"/"Release"/"GitHub" 等专有名词与 GitHub 返回的 message 保留英文。
- 品牌名 "GitHub Release Uploader"（strings.xml app_name）保留英文。

### 仓库概览页（2026-08-25 新增功能，commit b2b7f55 + 5536e03）

> 架构调整：`RepoDetail` 从"文件列表页"改为 **GitHub 官方 App 风格仓库概览页**，文件浏览拆为独立页面。

- **RepoDetailScreen（概览页）**：仓库信息头部（全名/描述/私人标签/星标/复刻）+ 标星按钮（PUT/DELETE `/user/starred`）+ 功能入口列表（议题/拉取请求/操作/发行版(最新 tag)/贡献者/关注者/代码）+ **README.md 直接渲染**（Markwon 重新引入，AndroidView + TextView）。
  - 入口点击打开浏览器对应页面（issues/pulls/actions/releases/contributors/watchers）；"代码"入口进文件浏览页。
  - 数据并行加载（`async`）：`getRepoDetail`/`getReleases`/`getContributors`/`getReadme`/`isStarred`。
- **RepoFilesScreen（文件浏览页，新增路由 `repo_files/{owner}/{repo}/{branch}`）**：原 RepoDetail 的目录导航 + Release 上传逻辑整体迁移至此（RepoFilesViewModel）。
- **API/模型扩展**：Repo 补充 `open_issues_count`/`watchers_count`/`owner` 等字段；新增 Contributor 模型、`getRepoDetail`/`getReleases`/`getContributors`/`getReadme`/`checkStarred`/`star`/`unstar` 接口。
- **注意**：`retrofit2.Response.code` 是 Java 方法必须 `code()` 带括号（曾致编译失败）；标星接口返回 204 无 body，用独立的 `safeApiCallVoid` 包装（`safeApiCall` 会把 204+空 body 误判失败）。

### 功能入口 App 内页面 + README 优化（2026-08-25 追加，commit b21bb51 + 7085ed2）

- 概览页 6 个功能入口**不再跳浏览器**，改为 App 内列表页：发行版 / 贡献者 / 关注者 / 议题 / 拉取请求 / 操作。
- 新增 6 个页面（各配 ViewModel）：`ReleasesScreen`（tag/名称/正文/时间/附件数）、`ContributorsScreen`（Coil 头像+贡献数）、`WatchersScreen`（Coil 头像）、`IssuesScreen`（绿点+#号+标题+时间）、`PullsScreen`（蓝分支+#号+标题）、`ActionsScreen`（工作流名+启用状态+路径）。
- API 扩展：`getSubscribers`/`getIssues`/`getPulls`/`getWorkflows`；新增 `Issue`/`PullRequest`/`Workflow`+`WorkflowResponse` 模型。
- NavGraph 用 `repoListScreen` 扩展统一注册 6 个列表路由（⚠️ 扩展函数内 `popBackStack` 需显式传 `NavHostController`，无隐式 receiver，曾致编译失败）。
- README 渲染优化：13sp 字体、1.3 行距、深浅色主题适配（`MaterialTheme.colorScheme.onSurface/surface`）、紧凑标题栏（Description 图标 + labelMedium）。

---

## 十、快速调试命令

```bash
# 查看仓库文件树
curl -s "https://api.github.com/repos/${REPO}/git/trees/main?recursive=1" \
  -H "Authorization: token $TOKEN" | python3 -c "import sys,json; [print(t['path']) for t in json.load(sys.stdin)['tree']]"

# 查看最新提交
curl -s "https://api.github.com/repos/${REPO}/commits?per_page=3" \
  -H "Authorization: token $TOKEN" | python3 -c "
import sys,json
for c in json.load(sys.stdin):
    print(f'{c[\"sha\"][:7]} {c[\"commit\"][\"message\"][:60]}')
"

# 查看工作流注释/警告
curl -s "https://api.github.com/repos/${REPO}/actions/runs/{run_id}" \
  -H "Authorization: token $TOKEN" | python3 -c "
import sys,json
d=json.load(sys.stdin)
cs=d['check_suite_id']
print(f'Check Suite: {cs}')
"
```

---

*此文档最后更新：2026-08-26*

### 详情页链路：Release 详情 + 工作流运行记录（2026-08-26 追加，commit a6ae780 + 13dff78 + fc608b8）> 用户反馈：发行版列表"显示不了详情和下载"、工作流"点不进去看具体情况"。补两层详情链路，全部 App 内完成，不跳浏览器。

- **发行版详情页 `ReleaseDetailScreen`（路由 `repo_release_detail/{owner}/{repo}/{releaseId}`，releaseId 用 `NavType.LongType`）**：
  - 列表项点击进入；显示完整 tag/名称/发布时间/完整 body + 附件（assets）列表（文件名/大小/下载次数）。
  - 附件点击用**系统 DownloadManager 下载**（无需存储权限）：API 29+ `setDestinationInExternalPublicDir`（公共下载目录），API 26-28 `setDestinationInExternalFilesDir`（App 专属目录）。
  - ⚠️ **两个 setDestination 方法都返回 `DownloadManager.Request`（不是 Boolean）**，不要写 `val ok = if (Q) ... else ...` 分支判断返回值（曾致两次编译失败），直接链式调用后 `enqueue` 即可。
- **工作流运行记录页 `WorkflowRunsScreen`（路由 `repo_workflow_runs/{owner}/{repo}/{workflowId}/{workflowName}`）**：工作流列表点击进入，显示该工作流所有运行（`GET /actions/workflows/{workflow_id}/runs`）：`#运行号` + 分支 + 时间 + **状态徽标**（`WorkflowStatusBadge`：结论优先——success 绿/failure 红/cancelled 灰/in_progress 黄/queued 蓝，放 `WorkflowRunsScreen.kt` 内，RunJobsScreen 同包复用）。
- **运行任务页 `RunJobsScreen`（路由 `repo_run_jobs/{owner}/{repo}/{runId}`）**：运行记录点击进入，显示 job 列表（`GET /actions/runs/{run_id}/jobs`）：名称 + 开始时间 + 状态徽标。
- **模型**：`WorkflowRun`（id/run_number/name/head_branch/status/conclusion/created_at/html_url）+ `WorkflowRunResponse`（total_count/workflow_runs）、`WorkflowRunJob`（id/name/status/conclusion/started_at）+ `WorkflowRunJobResponse`。
- **API/Repository 扩展**：`getRelease`（单 release 详情）、`getWorkflowRuns`、`getWorkflowRunJobs`，均 `retryable = true`。
- **注意**：Releases/Actions 因需要额外点击回调，改为**独立 composable 注册**（不再走 `repoListScreen` 通用函数）；`ActionsScreen` 的 `Modifier.clickable` 必须显式 `import androidx.compose.foundation.clickable`（该文件原本没有此 import，漏加会编译失败）。

### 创建 Release 移入发行版页 + README 字号 + job 日志（2026-08-26 追加，commit ebe2521 + 9881934）

> 用户反馈三点：①创建 Release 不该在代码页；②README 字体仍偏大；③工作流日志看不到具体内容、信息不全。

- **创建 Release 从代码页移到发行版页**：
  - `ReleasesScreen`：新增 FAB「新建发行版」→ 对话框输入 tag → 选文件 → `createRelease` 成功后启动 `UploadService` 上传附件（流程与原来一致：先选文件再建 Release，避免取消留下空 Release）；上传进度横幅 + 完成后自动刷新列表。
  - `RepoFilesScreen`/`RepoFilesViewModel`：**彻底移除** Release 创建 UI（Upload 图标按钮、对话框、文件选择器、上传进度横幅）及对应 ViewModel 状态/方法，代码页只保留目录浏览 + 代码查看。
  - ⚠️ 上传状态监听：`UploadService.uploadProgress` 是全局 StateFlow，页面进入时 `resetState()` 需判断非上传中，避免清掉正在进行的进度。
- **README 字号再缩小**：
  - `textSize` 13sp → **12sp**，行距保持 1.3。
  - ⚠️ Markwon 默认 heading 会放大到 **1.6 倍**（这就是"字体太大"的真正来源），必须自定义 SpanFactory 限制：`builder.setFactory(Heading::class.java) { _, props -> ... }`，级别取自 **`CoreProps.HEADING_LEVEL`**（`io.noties.markwon.core.CoreProps`，4.6.2 无 `HeadingProps` 类！），h1 1.2x / h2 1.1x / h3 1.05x，配 `RelativeSizeSpan` + `StyleSpan(BOLD)`。注意 `setFactory` 第一参要传 `Heading::class.java` 而非 `Heading`。
  - ⚠️⚠️ **二次放大 bug（README 字体偏大的真正根源）**：`TextView.textSize = 12f * ctx.resources.displayMetrics.scaledDensity` 是错的——`setTextSize(float)` 默认单位已是 sp（内部自动乘 scaledDensity），再手动乘一次等于 `12 * scaledDensity²`，字体放大 scaledDensity 倍。必须直接 `textSize = 12f`。代码页（CodeBrowserScreen 纯 Compose Text 13.sp）无此问题，所以"代码页正常、概览页偏大"。
- **工作流日志查看**：
  - `RunJobsScreen`：job 行可点击 → `JobLogsScreen`（路由 `repo_job_logs/{owner}/{repo}/{jobId}/{jobName}`）。
  - `JobLogsScreen`：等宽字体（`FontFamily.Monospace`）11sp 逐行显示日志全文，顶部「复制」按钮（ClipboardManager 整段复制）。
  - API：`GET /actions/jobs/{job_id}/logs`（GitHub 302 重定向到日志文本，**OkHttp 自动跟随**），Repository 包装为 `getJobLogs` 返回 `Result<String>`（`.map { it.string() }`）。
  - `WorkflowRun` 模型补 `event`（触发事件：push/workflow_dispatch 等）和 `actor`（User），运行记录行显示「分支 · 触发：事件 · 触发者 · 时间」。

### 工作流对齐官方 App + README 字号根治（2026-08-26 追加，commit 9fcd387）

> 用户反馈（对照 GitHub 官方 App 截图）：①概览页 README 字体仍偏大；②工作流详情信息少（官方有构建耗时、产物）；③日志全挤一块，官方是分步骤展示、失败红×/成功绿✓可精准定位。

- **README 字体偏大根治**：真正根源是 `textSize = 12f * scaledDensity` **二次放大**（setTextSize 默认 sp 已含 scaledDensity，再乘一次 = 平方放大）。已改为 `textSize = 12f`。配合上轮 Markwon heading 限制（1.2x 封顶），概览页 README 现在与功能入口文字相当。
- **运行详情页（RunJobsScreen 升级，路由不变 `repo_run_jobs/{owner}/{repo}/{runId}`）**：
  - 顶部概要卡片：`#运行号 名称` + 状态徽标 + **耗时**（`formatDuration(createdAt, completedAt)`，进行中算到当前时间）+ 触发：事件 · 触发者 + 分支。
  - **产物（Artifacts）区块**：`GET /actions/runs/{run_id}/artifacts`，显示名称/大小/过期时间，点击**下载 zip**——`archive_download_url` 需认证，走 Retrofit `@Streaming @GET downloadArtifact(@Url)`（AuthInterceptor 自动带 token），ViewModel 里流式写入 Downloads（API 29+ 公共目录/低版本 App 专属目录），Toast 提示路径。
  - job 行显示耗时 + 开始时间。
- **任务详情页（新，JobDetailScreen，路由 `repo_job_detail/{owner}/{repo}/{jobId}/{jobName}`）**：
  - `GET /actions/jobs/{job_id}` 返回 job 含 **steps** 数组。
  - 头部：job 名 + 状态徽标 + 耗时；步骤列表：**状态图标**（success 绿 ✓ CheckCircle / failure 红 ✗ Cancel / in_progress 黄圈 / skipped 灰 SkipNext）+ 步骤名（等宽字体）+ 每步耗时，点击步骤进日志页。
- **步骤日志页（新，StepLogsScreen，路由 `repo_step_logs/{owner}/{repo}/{jobId}/{stepNumber}/{stepName}`，替代旧 JobLogsScreen 已删除）**：
  - 完整 job 日志按 **`##[group]<步骤名>` / `##[endgroup]`** 标记切块（`StepLogsViewModel.extractStepLog`：①块标题精确匹配步骤名 → ②按 number-1 索引 → ③整段兜底）。
  - 逐行渲染：`##[error]` 标红、`##[warning]` 标黄、`##[notice]` 标蓝，去掉标记前缀；等宽 11sp；可复制当前步骤日志。
- **模型**：WorkflowRun 加 `completed_at`；WorkflowRunJob 加 `completed_at` + `steps: List<WorkflowRunStep>?`；新 WorkflowRunStep（id/name/status/conclusion/number/started_at/completed_at）、Artifact + ArtifactResponse。
- **API/Repository**：新增 `getWorkflowRun`（run 详情）、`getRunArtifacts`、`getJobDetail`、`downloadArtifact`（@Streaming 流式）；Repository 对应 4 方法。
- **共用工具**（放 `WorkflowRunsScreen.kt`，同包复用）：`formatDuration(startIso, endIso)` 用 `java.time.Instant` 解析（minSdk 26 可用），输出 "X分X秒"/"X小时X分"。
- ⚠️ 删除文件走 git trees API：`{'path': ..., 'sha': None}`（base_tree 不列出的文件会保留，必须显式 sha:null 删除）；RunJobsViewModel 注入 `@ApplicationContext` 用于产物落盘路径。

### 闪退修复 + 运行列表对齐官方 App（2026-08-26 追加，commit db729f7）

> 用户反馈：①点进日志 100% 闪退；②工作流列表项仍精简（官方：提交信息粗体 + 工作流名/#号/SHA + 时长/分支/相对时间标签）。

- **闪退根因（重要）**：GitHub job steps API 的 step 对象**没有 id 字段**（只有 name/status/conclusion/number/started_at/completed_at）。模型里 `WorkflowRunStep.id` 声明为必填 Long → Gson 全给 0 → `LazyColumn items(steps, key = { it.id })` **key 全部重复 → IllegalArgumentException 崩溃**。修复：`WorkflowRunStep` 删除 id 字段，`key = { it.number }`。
  - ⚠️ 教训：**凡是 GitHub API 不含 id 的列表元素（steps），key 必须用唯一业务字段（number），不能假设 id 存在**。items 的 key 重复是 100% 闪退。
- **运行列表项对齐官方 App（WorkflowRunsScreen.RunRow 重写）**：
  - 左侧状态图标（成功绿 ✓ CheckCircle / 失败红 ✗ Cancel / 进行中黄圈 / 跳过灰）——`runStatusVisual(status, conclusion)` 提取为同包 internal，JobDetail 步骤列表共用（删除了 JobDetail 私有副本）。
  - 主标题：**提交信息**（`head_commit.message` 首行，粗体，2 行截断），无提交信息时回退 `#运行号 名称`。
  - 第二行：`工作流名 · #运行号 · SHA 前 7 位`（灰字）。
  - 第三行：**圆角标签组** `MetaTag`（surfaceVariant 底 + labelSmall）：⏱ 耗时 / 🌿 分支 / 📅 **相对时间**（`timeAgo`："刚刚/X分钟前/X小时前/X天前"）。
- **模型**：WorkflowRun 加 `head_sha`、`head_commit`（新 `RunHeadCommit(message, sha)`）。

### 耗时计算修复 + 产物系统下载（2026-08-26 追加，commit 28d7e1a）

> 用户反馈：①列表/详情页"耗时 X小时"显示成几十小时，疑似把时间当耗时；②产物点击后 App 内一直转圈直到下载完；③希望长按复制下载链接。

- **耗时"伪数据"根因**：GitHub **runs API 没有 `completed_at` 字段**（那是 jobs 的）。原 `formatDuration(start, end)` 在 end 为 null 时 fallback 到 `Instant.now()`——把"距创建时间"当成了耗时，导致列表每条都显示"⏱ 2小时51分"（实际是几小时前创建的排队+等待时间），与 📅 相对时间标签几乎重复。
  - **修复**：①`formatDuration` 的 end 为 null **返回空字符串**（不再 fallback now），进行中由状态徽标体现；②超过 24 小时显示 **"X天X小时"**（不再"几十小时"）；③WorkflowRun 改用真实字段 `run_started_at`（实际开始，不含排队）→ `updated_at` 计算耗时（runs API 的 run 耗时正确口径）。
  - ⚠️ 教训：**runs 接口耗时 = run_started_at → updated_at；jobs 接口耗时 = started_at → completed_at**；created_at 含排队时间不可用于耗时。
- **产物下载改系统 DownloadManager**（不再 App 内转圈）：
  - `RunJobsViewModel.downloadArtifact`：`DownloadManager.Request.addRequestHeader("Authorization", "token xxx")` 带 token（`TokenManager.getToken()`），GitHub 302 重定向到**带签名的下载 URL**（无需再带 header），DownloadManager 可正常跟随；进度由系统通知栏展示。
  - API 29+ 存公共下载目录，26-28 存 App 专属目录（`setDestinationInExternalFilesDir`，两者都返回 Request 非 Boolean）。
- **产物长按复制下载链接**：`ArtifactRow` 用 `combinedClickable(onClick, onLongClick)`（`@OptIn(ExperimentalFoundationApi::class)`），长按复制 `archive_download_url` 到剪贴板并 Toast。
- 移除 `RunJobsUiState.isDownloading`（下载状态交给系统）；Repository 的 `downloadArtifactStream` 保留未删（当前无调用方）。

### 私人仓库附件下载 + README 链接 + 日志换行（2026-08-26 追加，commit ecfc6c7）

> 用户反馈：①私人仓库的发行版附件下载失败（官方 App 正常）；②README 里指向本仓库文档的链接点击跳浏览器；③日志页加自动换行按钮。

- **私人仓库附件下载失败修复**：`browser_download_url` 对 private repo 需要认证。原来 ReleaseDetailScreen 本地 `downloadAsset` 用 DownloadManager 直接下载**不带 Authorization** → 401 失败。改为 `ReleaseDetailViewModel.downloadAsset(asset)`（注入 `@ApplicationContext` + `TokenManager`），`addRequestHeader("Authorization", "token xxx")`，与产物下载同一模式；Screen 删掉本地下载函数，Toast 提示改走 `downloadMessage` state。
- **README 仓库内链接 App 内打开**：`ReadmeSection` 加 `onLinkClick` 回调，Markwon 插件里 `configureConfiguration { linkResolver { view, link -> ... } }`：
  - 链接去掉 `#锚点`/`?查询` 后，`http(s)://` 开头 → 保持浏览器打开（`Intent.ACTION_VIEW`）；其他相对路径 → `onReadmeLinkClick(path)` → NavGraph 导航到 `CodeBrowser` 代码页打开该文件。
  - ⚠️ `remember { }` 捕获的是首次闭包，回调必须用 `rememberUpdatedState` 包一层，否则 linkResolver 永远拿到旧回调。
- **日志自动换行按钮**：`StepLogsScreen` 加 `wrapLines` 开关（rememberSaveable，TopAppBar「换行：开/关」按钮）；开启时日志行 `Modifier.fillMaxWidth()` 自动换行占满屏宽，关闭时保持 `horizontalScroll` 横向滚动。

### 智谱 Round 5 审查处理（2026-08-26/27 追加，commit cf113c8 + 9e81517 + AI_HANDOFF）

> 智谱里程碑全读（46 文件/3900 行），B1-B8 缺陷 + O 系列。按约定逐条处理，不成立的写明理由。

**已修复（第一批全做 + 第二批最小改 + 部分 O）**：
- **B1（ANR）**：`getJobLogs`/`getReadme` 改 `withContext(Dispatchers.IO)`——日志/README 可达数 MB，`ResponseBody.string()`/Base64 解码不能再在主线程（viewModelScope.launch = Main）。
- **B2（截断）**：`getReleases` perPage 5→30、`getContributors` 10→30。分页机制（加载更多/自动翻页）留后续迭代。
- **B3（链接 404）**：README linkResolver 归一化——`./` 前缀、开头 `/` 剥掉；`mailto:`/`tel:` 交系统 Intent 不走仓库内。目录链接（contents API 返回数组致 Gson 异常）在 CodeBrowserViewModel 错误文案改为"此链接指向目录或无法解析的文件"。
- **B4（图片空白）**：README 图片渲染——加依赖 `io.noties.markwon:image:4.6.2`（ImagesPlugin 所在模块！image-coil **不传递**编译期依赖，漏加会编译失败）+ `image-coil:4.6.2`；Markwon 加 `ImagesPlugin.create()` + `CoilImagesPlugin.create(context)`；相对路径图片 `![alt](docs/x.png)` 预处理为 `https://raw.githubusercontent.com/{owner}/{repo}/{branch}/` 前缀（`resolveRelativeImageUrls`，remember 缓存）。
- **B6（过期产物可点）**：`ArtifactRow` 过期时 `alpha(0.5f)` + `combinedClickable(enabled = false)`。
- **B7①（冻结耗时）**：`status != "completed"` 时不显示耗时标签（列表 + run 详情卡）。
- **B8（议题数含 PR）**：文案改"${openIssuesCount} 个（含拉取请求）"。
- **O1**：StepLogsViewModel 按 jobId 缓存日志全文，切步骤不重复下载。
- **O3**：`formatSize` 提取 `utils/Formatters.kt`（RunJobs/ReleaseDetail 复用）。
- **O5**：`timeAgo` 加月/年档位（43200 分钟=30 天/525600=365 天）。
- **O6**：ReadmeSection `update` 用 `tv.tag` 比对 markdown 内容变化再 `setMarkdown`（README 长时避免重复 parse）。

**未修（写明理由）**：
- **B5（token 随 302 外泄）**：不修。OkHttp（getJobLogs）跨域重定向**自动剥离 Authorization**，无外泄；DownloadManager 虽重发 header，但目标均为 GitHub/Microsoft 自有签名 URL（`objects.githubusercontent.com`/`blob.core.windows.net`），实际可利用性极低，修法（先解析 Location 再下）复杂收益小。
- **B7②（刷新/轮询）**：P3 酌情项，本轮不做，后续可加 TopAppBar 刷新按钮。
- **O2（剥离时间戳）**：时间戳有排障价值，保留。
- **O4（Release body Markdown 渲染）**：Release body 以纯文本为主，改动大收益低。
- **O7（Issues/PR 状态 tab）**：与 B2 分页一起后续迭代。
- **O8**：已删 `downloadArtifactStream`/`api.downloadArtifact` 死代码（✓ 实际已做）。

### 智谱 Round 6 复审处理（2026-08-27 追加，commit 59553a8）

> 智谱对 Round5 修复做增量复审（含 OkHttp/Retrofit/Coil/markwon 四库源码级实证），确认 B1-B8/O 系列全部落地，并新发现 N1-N5。

**已修复**：
- **N1（P2，自报不符）**：`getReleases` perPage 实际仍是 5（Round5 的 5→30 因**并行 file_edit 编辑同一文件被互相覆盖丢失**——getContributors 改成了、getReleases 丢了）。已改 30。
- **N2（P2，B4 回归）**：`resolveRelativeImageUrls` 里 `substringBefore('#').substringBefore('?')` 在分支判断**之前**执行，shields.io 动态徽章等绝对 URL 的 query 被剥掉致 404。已改为**仅相对路径剥 #/?**，绝对 URL 原样保留。
- **N3（P3）**：`StepLogsViewModel.applyLog` 的 extractStepLog/split/filter 仍在主线程。已包 `withContext(Dispatchers.Default)`（用 suspend + withContext 而非嵌套 launch，避免连续切步骤竞态）。
- **N4（P3）**：引用式图片 `![alt][ref]` + `[ref]: docs/x.png` 不被内联正则覆盖。已在 `resolveRelativeImageUrls` 增加**引用定义替换**（行首 `[ref]: path` 正则，相对路径拼 raw 前缀）。

**未修（写明理由）**：
- **N5（P3）**：代码块内图片示例文本被正则误改写。极低频（README 教学示例），Markdown 代码块检测需完整状态机，改动复杂收益低，记录在案。

⚠️ **教训（再次验证）**：file_edit 并行编辑**同一文件**会互相覆盖（Round5 的 getReleases 改动就是这么丢的），同一文件的多次修改必须串行执行并事后 grep 验证。

---

*此文档最后更新：2026-08-27*
