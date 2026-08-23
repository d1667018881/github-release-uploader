# GitHub Release Uploader

一个基于 Kotlin + Jetpack Compose 的个人 GitHub Android 客户端，支持浏览仓库、查看代码、创建并上传 Release。

---

## 📋 项目速览

| 项目 | 信息 |
|------|------|
| 仓库地址 | `https://github.com/d1667018881/github-release-uploader` |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |
| 语言 | Kotlin 2.0+ |
| 构建工具 | Gradle 8.7 + AGP 8.5.2 |
| JDK | 17 |
| UI 框架 | Jetpack Compose + Material Design 3 |
| 架构 | MVVM + Repository |
| 依赖注入 | Hilt 2.51.1 |
| CI/CD | GitHub Actions |

---

## 🏗️ 技术栈详情

### 构建配置

| 组件 | 版本 | 说明 |
|------|------|------|
| Gradle | 8.7 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP | 8.5.2 | `build.gradle.kts`（根目录） |
| Kotlin | 2.0.0 | 含 `kotlin.plugin.compose` |
| KSP | 2.0.0-1.0.22 | Hilt 注解处理 |
| JDK | 17 | `compileOptions` + `kotlinOptions.jvmTarget` |

### 核心依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Compose BOM | 2024.06.00 | 统一管理 Compose 版本 |
| Material3 | BOM 管理 | Material Design 3 组件 |
| Navigation Compose | 2.7.7 | 页面导航 |
| Hilt | 2.51.1 | 依赖注入 |
| Retrofit | 2.11.0 | HTTP 网络请求 |
| OkHttp | 4.12.0 | HTTP 客户端 + 拦截器 |
| Security-Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |
| Coil | 2.6.0 | 图片加载 |
| Coroutines | 1.8.1 | 异步编程 |

### 签名配置

Release 构建使用 Android 默认 Debug 签名，**保证每次构建的 APK 签名一致，可覆盖安装**：

```kotlin
signingConfigs {
    getByName("debug")
}
buildTypes {
    getByName("release") {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("debug")
    }
}
```

---

## 📁 项目结构

```
github-release-uploader/
├── .github/workflows/android-build.yml   # CI/CD 工作流
├── app/
│   ├── build.gradle.kts                   # 应用级构建配置
│   ├── proguard-rules.pro                 # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/github/releaseuploader/
│       │   ├── GitHubApp.kt               # Application 入口（Hilt）
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   └── TokenManager.kt    # Token 加密存储
│       │   │   ├── model/
│       │   │   │   ├── User.kt            # GitHub 用户数据
│       │   │   │   ├── Repo.kt            # 仓库数据
│       │   │   │   ├── ContentItem.kt     # 文件/目录内容
│       │   │   │   └── Release.kt         # Release 数据
│       │   │   └── repository/
│       │   │       └── GitHubRepository.kt # 数据仓库层
│       │   ├── network/
│       │   │   ├── GitHubApi.kt           # Retrofit API 接口
│       │   │   ├── AuthInterceptor.kt     # 自动添加 Token 到 Header
│       │   │   ├── RateLimitInterceptor.kt # 限流检测与异常抛出
│       │   │   └── ProgressRequestBody.kt  # 上传进度回调
│       │   ├── di/
│       │   │   └── AppModule.kt           # Hilt 依赖注入模块
│       │   ├── service/
│       │   │   └── UploadService.kt       # 前台服务（上传保活）
│       │   ├── ui/
│       │   │   ├── MainActivity.kt        # 主 Activity
│       │   │   ├── navigation/
│       │   │   │   └── NavGraph.kt        # 导航图
│       │   │   ├── screens/
│       │   │   │   ├── LoginScreen.kt     # 登录页
│       │   │   │   ├── RepoListScreen.kt  # 仓库列表页
│       │   │   │   ├── RepoDetailScreen.kt# 仓库详情/目录浏览
│       │   │   │   └── CodeBrowserScreen.kt# 代码查看页
│       │   │   ├── theme/
│       │   │   │   ├── Color.kt           # 颜色定义（含 GitHub 配色）
│       │   │   │   ├── Type.kt            # 字体排版
│       │   │   │   └── Theme.kt           # 主题（深色/浅色模式）
│       │   │   └── viewmodel/
│       │   │       ├── LoginViewModel.kt
│       │   │       ├── RepoListViewModel.kt
│       │   │       ├── RepoDetailViewModel.kt
│       │   │       └── CodeBrowserViewModel.kt
│       │   └── utils/
│       │       └── Constants.kt           # 全局常量
│       └── res/
│           ├── values/
│           │   ├── strings.xml
│           │   └── themes.xml
│           ├── drawable/
│           │   ├── ic_launcher_foreground.xml
│           │   └── ic_launcher_background.xml
│           └── mipmap-anydpi-v26/
│               └── ic_launcher.xml
├── build.gradle.kts                       # 根构建配置
├── settings.gradle.kts                    # 项目设置
├── gradle.properties                      # Gradle 属性
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── gradlew                                # Gradle Wrapper（Unix）
└── gradlew.bat                            # Gradle Wrapper（Windows）
```

---

## 🔐 登录与安全

### 登录流程

1. 用户在登录页输入 GitHub Personal Access Token
2. 调用 `GET /user` 验证 Token 有效性
3. 验证成功后，Token 通过 `EncryptedSharedPreferences` 加密存储
4. Master Key 通过 `MasterKey.Builder` 动态生成（AES256-GCM），不硬编码

### 退出登录

- 清除 `EncryptedSharedPreferences` 中所有数据
- 重置导航栈，回到登录页

### Token 安全

- 使用 `androidx.security:security-crypto:1.1.0-alpha06`
- 文件加密：`AES256_SIV`
- 值加密：`AES256_GCM`

---

## 🌐 网络层

### OkHttp 拦截器链

```
请求 → AuthInterceptor → RateLimitInterceptor → LoggingInterceptor → 服务器
```

#### AuthInterceptor（认证拦截器）
- 自动从 `TokenManager` 读取 Token
- 添加到请求头：`Authorization: token {TOKEN}`
- 添加到请求头：`Accept: application/vnd.github+json`

#### RateLimitInterceptor（限流拦截器）
- 拦截 HTTP 403 响应（限流专用，401 token 无效由登录逻辑处理）
- 读取 `X-RateLimit-Remaining` Header
- 若为 0，通过 `StateFlow` 置位 `rateLimitExceeded`（不抛异常）
- UI 层观察到后强制退出登录，重新登录成功时自动 `reset()`

### GitHub API 接口

| 方法 | 端点 | 用途 |
|------|------|------|
| GET | `/user` | 验证 Token，获取用户信息 |
| GET | `/user/repos?per_page=30&page={page}` | 分页获取仓库列表 |
| GET | `/repos/{owner}/{repo}/contents/{path}` | 获取目录/文件内容 |
| POST | `/repos/{owner}/{repo}/releases` | 创建 Release |
| POST | `{upload_url}?name={file}` | 上传 Release 附件（raw body，非 multipart） |

---

## 📱 功能模块

### 1. 仓库列表（RepoListScreen）

- 展示用户所有仓库
- `LazyColumn` 滚动到底部自动加载下一页（分页 30 条/页）
- 显示仓库名称、描述、语言、Star 数、公开/私有状态
- 下拉刷新 + 退出登录

### 2. 仓库详情（RepoDetailScreen）

- 浏览仓库目录结构
- 点击目录进入子目录
- 点击文件进入代码查看
- 支持返回上级目录
- ActionBar 提供"上传 Release"入口

### 3. 代码浏览（CodeBrowserScreen）

- 读取并显示文件内容
- **Base64 解码**：GitHub API 返回 Base64 编码，自动解码
- **大文件保护**：文件 > 1MB 时提示用户去网页端查看
- 等宽字体显示，支持横向/纵向滚动
- 提供"在浏览器中打开"按钮

### 4. Release 上传

- 仓库详情页点击上传按钮
- 弹出 Dialog 输入 Tag 名（如 `v1.0.0`）
- 调用 API 创建 Release
- 自动打开文件选择器（`OpenMultipleDocuments`，`*/*` MIME）
- 多文件选择后通过前台服务真实上传（`UploadService` 注入 `GitHubRepository`）
- `ProgressRequestBody` 按字节块计算上传进度
- 失败自动重试（默认 3 次，`uploadAssetWithRetry`）

### 5. 前台服务（UploadService）

- 上传期间运行前台服务
- 不可清除的通知，显示实时进度
- 上传完成/失败自动更新通知状态

---

## 🎨 UI 设计

### 深色模式

自动跟随系统设置，深色主题使用 GitHub 风格配色：

| 颜色 | 用途 |
|------|------|
| `#0D1117` | 背景色 |
| `#161B22` | 表面色 |
| `#58A6FF` | 主色调（蓝色） |
| `#3FB950` | 辅助色（绿色） |
| `#F85149` | 强调色（红色） |

### Material Design 3

- 使用 `MaterialTheme` 统一主题
- 自适应状态栏颜色
- 完整的深浅色主题切换

---

## 🔄 CI/CD 工作流

### 触发条件

- `push` 到 `main` 分支

### 工作流步骤

```
1. Checkout 代码         → actions/checkout@v4
2. 安装 JDK 17           → actions/setup-java@v5
3. 配置 Gradle           → gradle/actions/setup-gradle@v4
4. 构建 Release APK      → ./gradlew assembleRelease -PversionCode=${{ github.run_number }}
5. 上传构建产物           → actions/upload-artifact@v4
6. 创建 Release + 上传APK → softprops/action-gh-release@v2
```

### 版本号自动递增

- `versionCode`: 使用 `github.run_number`（每次构建自动 +1）
- `versionName`: `1.0.{run_number}`
- Tag 格式: `v1.0.{run_number}`

### Release 发布

- 构建成功后自动创建 GitHub Release
- 自动生成 Release Notes
- APK 作为 Release 附件上传

---

## 🐛 已知问题与修复记录

### 构建问题修复历史

| # | 错误 | 原因 | 修复 |
|---|------|------|------|
| 1 | `Could not find or load main class "-Xmx64m"` | gradlew 中 JVM 参数引号格式错误 | 修复 `DEFAULT_JVM_OPTS` 的引号 |
| 2 | `Unresolved reference: dependencyResolution` | Gradle 8.7 不支持 `dependencyResolution` | 改为 `dependencyResolutionManagement` |
| 3 | `Accidental override: getContentResolver()` | Service 基类与 Hilt 注入的 contentResolver 冲突 | 移除 UploadService 中 `@Inject contentResolver` |
| 4 | Release 创建 403 | `GITHUB_TOKEN` 默认权限不足 | 添加 `permissions: contents: write` |

### 依赖冲突处理策略

当遇到依赖冲突时：
1. 检查 `app/build.gradle.kts` 中的版本号
2. 查找兼容版本（参考 [AndroidX Release Notes](https://developer.android.com/jetpack/androidx/versions)）
3. 确保 AGP 版本与 Gradle 版本兼容：
   - AGP 8.5.x → Gradle 8.7+
   - Kotlin 2.0.x → AGP 8.5+
4. Compose 相关版本通过 BOM 统一管理，避免手动指定

### 常见错误排查

| 错误 | 检查项 |
|------|--------|
| Hilt 编译错误 | 确保 `@HiltAndroidApp`、`@AndroidEntryPoint`、`@HiltViewModel` 正确添加 |
| 网络请求 401 | Token 过期或无效，重新登录 |
| 网络请求 403 | API 限流，检查 Rate Limit 状态 |
| 文件过大无法查看 | 大于 1MB 的文件提示去网页端查看 |
| 签名不一致无法覆盖安装 | Release 使用 Debug 签名，确保卸载旧版本后重装或使用同一签名 |

---

## 🚀 本地开发指南

### 环境要求

- JDK 17
- Android SDK（API 34）
- Android Studio（推荐最新版）

### 克隆并构建

```bash
# 克隆仓库
git clone https://github.com/d1667018881/github-release-uploader.git
cd github-release-uploader

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 指定版本号构建
./gradlew assembleRelease -PversionCode=100
```

### 本地运行

1. 用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器（API 26+）
4. 点击 Run

### 代码规范

- 包名：`com.github.releaseuploader.{ui,data,network,di,service,utils}`
- 架构：MVVM + Repository
- ViewModel 通过 Hilt 注入，使用 `StateFlow` 管理 UI 状态
- 网络请求通过 `GitHubRepository` 封装，返回 `Result<T>`
- 敏感数据使用 `EncryptedSharedPreferences`

---

## 🤖 AI 接手项目指南

如果你是一个 AI 助手（如 Claude、GPT、MClaw 等），接手此项目时的操作流程：

### 快速上手步骤

1. **阅读本文档**：了解项目架构、技术栈和目录结构
2. **查看最新构建状态**：
   ```bash
   curl -s "https://api.github.com/repos/d1667018881/github-release-uploader/actions/runs?per_page=1" \
     -H "Authorization: token {TOKEN}" \
     -H "Accept: application/vnd.github+json"
   ```
3. **修改代码**：按照目录结构找到对应文件进行修改
4. **推送代码**：使用 GitHub Contents API 或 Git Push
5. **监控构建**：轮询 Actions 运行状态，如有失败则下载日志分析
6. **自动修复**：根据错误信息修改代码，重新推送，最多重试 5 次

### 修改文件的标准流程

```
1. 读取文件    → 使用 GitHub Contents API 获取当前内容和 SHA
2. 修改内容    → 在本地修改文件
3. 提交推送    → 使用 GitHub Contents API 的 PUT 更新（需要 SHA）
4. 等待构建    → 轮询 GitHub Actions API
5. 检查结果    → 成功 → 继续；失败 → 下载日志 → 修复 → 回到步骤 1
```

### 使用 GitHub API 更新文件（示例）

```bash
TOKEN="your_token"
REPO="d1667018881/github-release-uploader"

# 1. 获取文件 SHA
SHA=$(curl -s "https://api.github.com/repos/${REPO}/contents/path/to/file" \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" | python3 -c "import sys,json; print(json.load(sys.stdin)['sha'])")

# 2. 更新文件
CONTENT=$(cat local_file)
B64=$(echo -n "$CONTENT" | base64 -w0)
curl -s -X PUT "https://api.github.com/repos/${REPO}/contents/path/to/file" \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -d "{\"message\":\"commit message\",\"content\":\"$B64\",\"sha\":\"$SHA\"}"
```

### 构建失败自动修复策略

```
1. 下载失败日志
2. 解析编译错误
3. 依赖冲突 → 修改 build.gradle.kts 版本号
4. 语法错误 → 修改对应 .kt 文件
5. 配置错误 → 修改 settings.gradle.kts 或 workflow
6. 提交并推送
7. 重新进入轮询
8. 最多重试 5 次，超过则报告最终错误
```

### 注意事项

- ⚠️ **不要修改 `.gitignore` 中已忽略的文件**
- ⚠️ **Release 类型使用 Debug 签名**，不要修改签名配置
- ⚠️ **权限问题**：GitHub Actions 需要 `contents: write` 权限才能创建 Release
- ⚠️ **版本号**：通过 `-PversionCode=N` 传递，不要硬编码
- ⚠️ **Token 安全**：Token 只在 `TokenManager` 中通过加密存储，不在其他地方硬编码

---

## 📄 许可证

MIT License

---

*最后更新：2026-08-23*se

---

*最启�

新：2026-08-23*
*最后更新：2026-08-23*