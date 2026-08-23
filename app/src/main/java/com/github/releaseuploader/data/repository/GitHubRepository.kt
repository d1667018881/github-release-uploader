package com.github.releaseuploader.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.github.releaseuploader.data.model.*
import com.github.releaseuploader.network.GitHubApi
import com.github.releaseuploader.network.ProgressRequestBody
import com.github.releaseuploader.utils.Constants
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepository @Inject constructor(
    private val api: GitHubApi
) {

    suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = api.getCurrentUser()
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("Failed to fetch user: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserRepos(page: Int): Result<List<Repo>> {
        return try {
            val response = api.getUserRepos(perPage = Constants.PER_PAGE, page = page)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(IOException("Failed to fetch repos: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getContents(owner: String, repo: String, path: String): Result<List<ContentItem>> {
        return try {
            val response = api.getContents(owner, repo, path)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(IOException("Failed to fetch contents: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileContent(owner: String, repo: String, path: String): Result<ContentItem> {
        return try {
            val response = api.getFileContent(owner, repo, path)
            if (response.isSuccessful) {
                val item = response.body()!!
                if (item.size > Constants.MAX_FILE_SIZE_BYTES) {
                    Result.failure(IOException("File too large (${item.size} bytes). Please view on GitHub website."))
                } else {
                    Result.success(item)
                }
            } else {
                Result.failure(IOException("Failed to fetch file: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("Failed to create release: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAsset(
        uploadUrl: String,
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String?
    ): ProgressRequestBody {
        val mediaType = mimeType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val progressBody = ProgressRequestBody(contentResolver, uri, mediaType, fileName)
        val part = MultipartBody.Part.createFormData("file", fileName, progressBody)
        api.uploadReleaseAsset(uploadUrl, part)
        return progressBody
    }

    suspend fun uploadAssetWithRetry(
        uploadUrl: String,
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        maxRetries: Int = Constants.MAX_UPLOAD_RETRIES
    ): Result<Unit> {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val progressBody = uploadAsset(uploadUrl, contentResolver, uri, fileName, mimeType)
                return Result.success(Unit)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(1000L * attempt)
                }
            }
        }
        return Result.failure(lastException ?: IOException("Upload failed after $maxRetries retries"))
    }
}
