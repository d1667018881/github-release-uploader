package com.github.releaseuploader.data.model

import com.google.gson.annotations.SerializedName

/** 工作流的一次运行记录（actions/workflows/{id}/runs） */
data class WorkflowRun(
    val id: Long,
    @SerializedName("run_number") val runNumber: Int,
    val name: String?,
    @SerializedName("head_branch") val headBranch: String?,
    val status: String,
    val conclusion: String?,
    /** 触发事件：push / workflow_dispatch / pull_request 等 */
    val event: String?,
    val actor: User?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("html_url") val htmlUrl: String
)

data class WorkflowRunResponse(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("workflow_runs") val workflowRuns: List<WorkflowRun>
)

/** 运行记录里的一个 job（actions/runs/{id}/jobs / actions/jobs/{id}） */
data class WorkflowRunJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("completed_at") val completedAt: String?,
    val steps: List<WorkflowRunStep>? = null
)

data class WorkflowRunJobResponse(
    @SerializedName("total_count") val totalCount: Int,
    val jobs: List<WorkflowRunJob>
)

/** job 里的一个执行步骤 */
data class WorkflowRunStep(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("completed_at") val completedAt: String?
)

/** 运行产物（actions/runs/{id}/artifacts） */
data class Artifact(
    val id: Long,
    val name: String,
    @SerializedName("size_in_bytes") val sizeInBytes: Long,
    @SerializedName("archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean,
    @SerializedName("expires_at") val expiresAt: String
)

data class ArtifactResponse(
    @SerializedName("total_count") val totalCount: Int,
    val artifacts: List<Artifact>
)
