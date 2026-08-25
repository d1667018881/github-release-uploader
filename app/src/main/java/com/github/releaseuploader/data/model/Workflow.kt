package com.github.releaseuploader.data.model

import com.google.gson.annotations.SerializedName

data class Workflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

data class WorkflowResponse(
    @SerializedName("total_count") val totalCount: Int,
    val workflows: List<Workflow>
)
