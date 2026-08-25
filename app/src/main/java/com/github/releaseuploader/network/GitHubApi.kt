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

    // ---- 仓库概览页 ----
    @GET("repos/{owner}/{repo}")
    suspend fun getRepoDetail(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<Repo>

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 5
    ): Response<List<Release>>

    @GET("repos/{owner}/{repo}/contributors")
    suspend fun getContributors(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): Response<List<Contributor>>

    @GET("repos/{owner}/{repo}/subscribers")
    suspend fun getSubscribers(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): Response<List<User>>

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 30
    ): Response<List<Issue>>

    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPulls(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 30
    ): Response<List<PullRequest>>

    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<WorkflowResponse>

    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<ContentItem>

    // 标星（204 无 body；check 时 204=已标星，404=未标星）
    @GET("user/starred/{owner}/{repo}")
    suspend fun checkStarred(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<ResponseBody>

    @PUT("user/starred/{owner}/{repo}")
    suspend fun starRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<ResponseBody>

    @DELETE("user/starred/{owner}/{repo}")
    suspend fun unstarRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<ResponseBody>
}
