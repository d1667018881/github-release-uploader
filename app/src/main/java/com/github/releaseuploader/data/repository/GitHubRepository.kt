package com.github.releaseuploader.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.github.releaseuploader.data.model.*
import com.github.releaseuploader.network.ApiException
import com.github.releaseuploader.network.GitHubApi
import com.github.releaseuploader.network.ProgressRequestBody
import com.github.releaseuploader.utils.Constants
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepository @Inject constructor(
    private val api: GitHubApi
) {

    // 轻量 LRU 内存缓存：降低重复请求、减少 API 配额消耗与限流风险。
    // 只缓存静态数据（目录/文件内容），登出时 clearCache() 清理。
    // 仓库列表是动态数据（星标/更新时间会变），不做缓存，避免脏数据。
    // 线程安全：clearCache() 可能被 SessionManager 在 IO 线程调用，
    // 而 getContents/getFileContent 可能在主线程写缓存，必须加锁。
    private val cacheLock = Any()
    private val contentsCache = LinkedHashMap<String, List<ContentItem>>(32, 0.75f, true)
    private val fileCache = LinkedHashMap<String, ContentItem>(16, 0.75f, true)

    fun clearCache() {
        synchronized(cacheLock) {
            contentsCache.clear()
            fileCache.clear()
        }
    }

    // GET 请求带重试（网络抖动时自动重试一次）；POST 不重试
    suspend fun getCurrentUser(): Result<User> = safeApiCall(retryable = true) { api.getCurrentUser() }

    suspend fun getUserRepos(page: Int): Result<List<Repo>> = safeApiCall(retryable = true) {
        api.getUserRepos(perPage = Constants.PER_PAGE, page = page)
    }

    suspend fun getContents(owner: String, repo: String, path: String): Result<List<ContentItem>> {
        val key = "$owner/$repo/$path"
        synchronized(cacheLock) {
            contentsCache[key]?.let { return Result.success(it) }
        }
        return safeApiCall(retryable = true) { api.getContents(owner, repo, path) }.onSuccess { contents ->
            synchronized(cacheLock) {
                contentsCache[key] = contents
                trimCache(contentsCache, Constants.MAX_CONTENTS_CACHE_SIZE)
            }
        }
    }

    suspend fun getFileContent(owner: String, repo: String, path: String): Result<ContentItem> {
        val key = "$owner/$repo/$path"
        synchronized(cacheLock) {
            fileCache[key]?.let { return Result.success(it) }
        }
        val result = safeApiCall(retryable = true) { api.getFileContent(owner, repo, path) }
        return result.fold(
            onSuccess = { item ->
                if (item.size > Constants.MAX_FILE_SIZE_BYTES) {
                    Result.failure(IOException("文件过大（${item.size} 字节），请在 GitHub 网页端查看。"))
                } else {
                    synchronized(cacheLock) {
                        fileCache[key] = item
                        trimCache(fileCache, Constants.MAX_FILE_CACHE_SIZE)
                    }
                    Result.success(item)
                }
            },
            onFailure = { e ->
                val cached = synchronized(cacheLock) { fileCache[key] }
                if (cached != null) {
                    Log.w(TAG, "Network failed for $key, returning cached data: ${e.message}")
                    Result.success(cached)
                } else {
                    Result.failure(e)
                }
            }
        )
    }

    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String? = null,
        body: String? = null
    ): Result<Release> = safeApiCall {
        api.createRelease(
            owner, repo,
            CreateReleaseRequest(tagName = tagName, name = name ?: tagName, body = body)
        )
    }

    suspend fun uploadAsset(
        uploadUrl: String,
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        val mediaType = mimeType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val progressBody = ProgressRequestBody(contentResolver, uri, mediaType, onProgress)
        val resolvedUrl = resolveUploadUrl(uploadUrl, fileName)
        return safeApiCall { api.uploadReleaseAsset(resolvedUrl, progressBody) }.map { }
    }

    suspend fun uploadAssetWithRetry(
        uploadUrl: String,
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        onProgress: (Float) -> Unit = {},
        maxRetries: Int = Constants.MAX_UPLOAD_RETRIES
    ): Result<Unit> {
        var lastResult: Result<Unit> = Result.failure(IOException("重试 $maxRetries 次后上传失败"))
        for (attempt in 1..maxRetries) {
            lastResult = uploadAsset(uploadUrl, contentResolver, uri, fileName, mimeType, onProgress)
            if (lastResult.isSuccess) return lastResult
            // 只对网络层错误与 5xx 重试；4xx（422 重名、401 鉴权）重试无意义
            if (attempt < maxRetries && shouldRetry(lastResult.exceptionOrNull())) {
                kotlinx.coroutines.delay(1000L * attempt)
            }
        }
        return lastResult
    }

    // ---- 仓库概览页 ----
    suspend fun getRepoDetail(owner: String, repo: String): Result<Repo> =
        safeApiCall(retryable = true) { api.getRepoDetail(owner, repo) }

    suspend fun getReleases(owner: String, repo: String): Result<List<Release>> =
        safeApiCall(retryable = true) { api.getReleases(owner, repo) }

    suspend fun getRelease(owner: String, repo: String, releaseId: Long): Result<Release> =
        safeApiCall(retryable = true) { api.getRelease(owner, repo, releaseId) }

    suspend fun getContributors(owner: String, repo: String): Result<List<Contributor>> =
        safeApiCall(retryable = true) { api.getContributors(owner, repo) }

    suspend fun getSubscribers(owner: String, repo: String): Result<List<User>> =
        safeApiCall(retryable = true) { api.getSubscribers(owner, repo) }

    suspend fun getIssues(owner: String, repo: String): Result<List<Issue>> =
        safeApiCall(retryable = true) { api.getIssues(owner, repo) }

    suspend fun getPulls(owner: String, repo: String): Result<List<PullRequest>> =
        safeApiCall(retryable = true) { api.getPulls(owner, repo) }

    suspend fun getWorkflows(owner: String, repo: String): Result<List<Workflow>> =
        safeApiCall(retryable = true) { api.getWorkflows(owner, repo) }.map { it.workflows }

    suspend fun getWorkflowRuns(owner: String, repo: String, workflowId: Long): Result<List<WorkflowRun>> =
        safeApiCall(retryable = true) { api.getWorkflowRuns(owner, repo, workflowId) }.map { it.workflowRuns }

    suspend fun getWorkflowRunJobs(owner: String, repo: String, runId: Long): Result<List<WorkflowRunJob>> =
        safeApiCall(retryable = true) { api.getWorkflowRunJobs(owner, repo, runId) }.map { it.jobs }

    /** 获取 job 日志全文（GitHub 302 重定向到日志文本，OkHttp 自动跟随） */
    suspend fun getJobLogs(owner: String, repo: String, jobId: Long): Result<String> =
        safeApiCall(retryable = true) { api.getJobLogs(owner, repo, jobId) }
            .map { it.string() }

    /** 获取 README 并直接返回解码后的 Markdown 文本 */
    suspend fun getReadme(owner: String, repo: String): Result<String> {
        val result = safeApiCall(retryable = true) { api.getReadme(owner, repo) }
        return result.map { item ->
            if (item.encoding == "base64" && item.content != null) {
                val clean = item.content.replace("\n", "").replace("\r", "")
                String(Base64.decode(clean, Base64.DEFAULT), Charsets.UTF_8)
            } else {
                ""
            }
        }
    }

    /** 是否已标星：204=已标星，404=未标星，其余网络错误按未标星处理 */
    suspend fun isStarred(owner: String, repo: String): Boolean {
        return try {
            // retrofit2.Response.code 是 Java 方法，必须带括号调用
            api.checkStarred(owner, repo).code() == 204
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun star(owner: String, repo: String): Result<Unit> =
        safeApiCallVoid { api.starRepo(owner, repo) }

    suspend fun unstar(owner: String, repo: String): Result<Unit> =
        safeApiCallVoid { api.unstarRepo(owner, repo) }

    /** 无 body 接口（标星 PUT/DELETE 返回 204）的统一包装 */
    private suspend fun safeApiCallVoid(call: suspend () -> retrofit2.Response<okhttp3.ResponseBody>): Result<Unit> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(ApiException(response.code(), "HTTP 请求失败（${response.code()}）：${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GitHub createRelease 返回的 upload_url 形如
     * https://uploads.github.com/repos/{owner}/{repo}/releases/{id}/assets{?name,label}
     * 上传前必须把 {?name,label} 模板替换为 ?name=<urlencoded fileName>。
     */
    private fun resolveUploadUrl(uploadUrl: String, fileName: String): String {
        val encodedName = Uri.encode(fileName)
        return when {
            uploadUrl.contains("{?name,label}") ->
                uploadUrl.replace("{?name,label}", "?name=$encodedName")
            uploadUrl.contains("{?name}") ->
                uploadUrl.replace("{?name}", "?name=$encodedName")
            uploadUrl.contains("?") -> "$uploadUrl&name=$encodedName"
            else -> "$uploadUrl?name=$encodedName"
        }
    }

    /**
     * 统一的 API 调用包装：取消透传 + 空 body 防护 + HTTP 错误带状态码。
     * retryable=true（GET）时对网络错误/5xx 自动重试一次。
     * 注意：call 必须是最后一个参数，否则 trailing lambda 无法绑定（会绑到 retryable 导致类型错误）。
     */
    private suspend fun <T> safeApiCall(
        retryable: Boolean = false,
        call: suspend () -> Response<T>
    ): Result<T> {
        val maxAttempts = if (retryable) Constants.MAX_GET_RETRIES else 1
        var lastResult: Result<T>? = null
        for (attempt in 1..maxAttempts) {
            lastResult = try {
                val response = call()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.failure(ApiException(response.code(), "响应体为空"))
                    }
                } else {
                    Result.failure(ApiException(response.code(), "HTTP 请求失败（${response.code()}）：${response.message()}"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (lastResult.isSuccess) break
            val canRetry = lastResult.exceptionOrNull()?.let { shouldRetry(it) } == true
            if (attempt < maxAttempts && canRetry) {
                kotlinx.coroutines.delay(500L * attempt)
            } else {
                break
            }
        }
        return lastResult ?: Result.failure(IOException("请求失败"))
    }

    private fun shouldRetry(e: Throwable?): Boolean = when (e) {
        is ApiException -> e.code >= 500
        is IOException -> true
        else -> false
    }

    private fun <K, V> trimCache(cache: LinkedHashMap<K, V>, maxSize: Int) {
        while (cache.size > maxSize) {
            cache.remove(cache.keys.first())
        }
    }

    private companion object {
        const val TAG = "GitHubRepository"
    }
}
