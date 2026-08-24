package com.github.releaseuploader.network

import com.github.releaseuploader.data.model.*
import com.github.releaseuploader.utils.Constants
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GitHubApi {

    @GET("user")
    suspend fun getCurrentUser(): Response<User>

    @GET("user/repos")
    suspend fun getUserRepos(
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "updated"
    ): Response<List<Repo>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): Response<List<ContentItem>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): Response<ContentItem>

    @POST("repos/{owner}/{repo}/releases")
    suspend fun createRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateReleaseRequest
    ): Response<Release>

    // GitHub 上传 Release 附件走 raw body + ?name= 查询参数，不是 multipart。
    // uploadUrl 需先用 GitHubRepository.resolveUploadUrl() 把 {?name,label} 模板替换为 ?name=<fileName>。
    @POST
    suspend fun uploadReleaseAsset(
        @Url uploadUrl: String,
        @Body file: RequestBody
    ): Response<ResponseBody>
}
