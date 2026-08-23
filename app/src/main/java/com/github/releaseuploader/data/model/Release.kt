package com.github.releaseuploader.data.model

import com.google.gson.annotations.SerializedName

data class Release(
    val id: Long,
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("upload_url") val uploadUrl: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("assets") val assets: List<ReleaseAsset>? = null
)

data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerializedName("download_count") val downloadCount: Int,
    @SerializedName("browser_download_url") val browserDownloadUrl: String
)

data class CreateReleaseRequest(
    @SerializedName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false
)
