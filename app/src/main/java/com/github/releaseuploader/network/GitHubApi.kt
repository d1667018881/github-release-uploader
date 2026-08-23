package com.github.releaseuploader.network

import com.github.releaseuploader.data.model.*
import okhttp3.MultipartBody
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

    @Multipart
    @POST
    suspend fun uploadReleaseAsset(
        @Url uploadUrl: String,
        @Part file: MultipartBody.Part
    ): Response<ResponseBody>

    @Multipart
    @POST
    suspend fun uploadReleaseAssetWithName(
        @Url uploadUrl: String,
        @Part file: MultipartBody.Part,
        @Part("name") name: RequestBody
    ): Response<ResponseBody>
}
