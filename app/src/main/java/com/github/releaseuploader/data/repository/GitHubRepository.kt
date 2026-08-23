package com.github.releaseuploader.data.repository

import android.content.ContentResolver
import android.net.Uri
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
    private val contentsCache = LinkedHashMap<String, List<ContentItem>>(32, 0.75f, true)
    private val fileCache = LinkedHashMap<String, ContentItem>(16, 0.75f, true)

    fun clearCache() {
        contentsCache.clear()
        fileCache.clear()
    }

    suspend fun getCurrentUser(): Result<User> = safeApiCall { api.getCurrentUser() }

    suspend fun getUserRepos(page: Int): Result<List<Repo>> = safeApiCall {
        api.getUserRepos(perPage = Constants.PER_PAGE, page = page)
    }

    suspend fun getContents(owner: String, repo: String, path: String): Result<List<ContentItem>> {
        val key = "$owner/$repo/$path"
        contentsCache[key]?.let { return Result.success(it) }
        return safeApiCall { api.getContents(owner, repo, path) }.onSuccess { contents ->
            contentsCache[key] = contents
            trimCache(contentsCache, Constants.MAX_CONTENTS_CACHE_SIZE)
        }
    }

    suspend fun getFileContent(owner: String, repo: String, path: String): Result<ContentItem> {
        val key = "$owner/$repo/$path"
        fileCache[key]?.let { return Result.success(it) }
        val result = safeApiCall { api.getFileContent(owner, repo, path) }
        return result.fold(
            onSuccess = { item ->
                if (item.size > Constants.MAX_FILE_SIZE_BYTES) {
                    Result.failure(IOException("File too large (${item.size} bytes). Please view on GitHub website."))
                } else {
                    fileCache[key] = item
                    trimCache(fileCache, Constants.MAX_FILE_CACHE_SIZE)
                    Result.success(item)
                }
            },
            onFailure = { e ->
                fileCache[key]?.let { return@fold Result.success(it) }
                Result.failure(e)
            }
        )
    }

    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String? = null,
        body: String? = null
    ): Result<Release> {
        return try {
            val request = CreateReleaseRequest(
                tagName = tagName,
                name = name ?: tagName,
                body = body
            )
            val response = api.createRelease(owner, repo, request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(ApiException(response.code(), "Empty response body"))
                }
            } else {
                Result.failure(ApiException(response.code(), "Failed to create release: ${response.code()} ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAsset(
        uploadUrl: String,
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        return try {
            val mediaType = mimeType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
            val progressBody = ProgressRequestBody(contentResolver, uri, mediaType, onProgress)
            val resolvedUrl = resolveUploadUrl(uploadUrl, fileName)
            val response = api.uploadReleaseAsset(resolvedUrl, progressBody)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(ApiException(response.code(), "Upload failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        var lastResult: Result<Unit> = Result.failure(IOException("Upload failed after $maxRetries retries"))
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

    /** 统一的 API 调用包装：取消透传 + 空 body 防护 + HTTP 错误带状态码 */
    private suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(ApiException(response.code(), "Empty response body"))
                }
            } else {
                Result.failure(ApiException(response.code(), "HTTP ${response.code()} ${response.message()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
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
}
