package com.github.releaseuploader.data.model

import com.google.gson.annotations.SerializedName

data class ContentItem(
    val name: String,
    val path: String,
    val type: String, // "file" or "dir"
    val size: Long = 0,
    val content: String? = null,
    val encoding: String? = null,
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null
)
