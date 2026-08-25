package com.github.releaseuploader.data.model

import com.google.gson.annotations.SerializedName

data class Repo(
    val id: Long,
    val name: String,
    @SerializedName("full_name") val fullName: String,
    val description: String?,
    @SerializedName("private") val isPrivate: Boolean,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("stargazers_count") val stargazersCount: Int,
    @SerializedName("forks_count") val forksCount: Int,
    @SerializedName("language") val language: String?,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("default_branch") val defaultBranch: String,
    // 概览页补充字段（/repos/{owner}/{repo} 返回）
    @SerializedName("open_issues_count") val openIssuesCount: Int = 0,
    @SerializedName("watchers_count") val watchersCount: Int = 0,
    @SerializedName("subscribers_count") val subscribersCount: Int = 0,
    @SerializedName("has_issues") val hasIssues: Boolean = true,
    val owner: RepoOwner? = null
)

data class RepoOwner(
    val login: String,
    @SerializedName("avatar_url") val avatarUrl: String?
)
