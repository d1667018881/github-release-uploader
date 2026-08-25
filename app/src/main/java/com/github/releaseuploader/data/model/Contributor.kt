package com.github.releaseuploader.data.model

import com.google.gson.annotations.SerializedName

data class Contributor(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val contributions: Int
)
