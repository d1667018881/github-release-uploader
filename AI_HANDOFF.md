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

---

## 四、构建监控与自动修复

### 监控构建状态

```bash
for i in $(seq 1 40); do
    sleep 15
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
- 修改 `gradle-wrapper.properties` 中的 distributionUrl 为不兼容版本
- 在未验证兼容性的情况下大幅度升级 Gradle/AGP/Kotlin

---

## 九、快速调试命令

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