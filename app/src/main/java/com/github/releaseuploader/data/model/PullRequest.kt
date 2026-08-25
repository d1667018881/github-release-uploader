package com.github.releaseuploader.data.model

import com.google.gson.annotations.SerializedName

data class PullRequest(
    val number: Int,
    val title: String,
    val state: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("created_at") val createdAt: String,
    val user: User?
)
