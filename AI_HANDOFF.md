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
- 修改 Release 的签名配置（必须使用 Debug 签名）
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
| release 换正式签名 | debug 签名是项目**有意为之**（CI 每次构建签名一致、可覆盖安装，适合个人自分发）；正式签名需 keystore 注入 CI secrets，属分发策略升级而非代码缺陷 |
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
- `UploadService.uploadProgress` 为 companion 全局 StateFlow，上传完成后 `isComplete` 会残留（重进页面可能看到旧「Upload complete!」），可在页面进入时按需 `resetState()`。

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

*此文档最后更新：2026-08-23*
