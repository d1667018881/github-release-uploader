package com.github.releaseuploader.ui.viewmodel

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.local.TokenManager
import com.github.releaseuploader.data.model.Artifact
import com.github.releaseuploader.data.model.WorkflowRun
import com.github.releaseuploader.data.model.WorkflowRunJob
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunJobsUiState(
    val run: WorkflowRun? = null,
    val jobs: List<WorkflowRunJob> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadMessage: String? = null,
    val isLoggedOut: Boolean = false
)

/** 运行详情页：run 概要（状态/耗时/触发）+ 产物 + job 列表 */
@HiltViewModel
class RunJobsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunJobsUiState())
    val uiState: StateFlow<RunJobsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
            }
        }
    }

    fun loadRunDetail(owner: String, repo: String, runId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val runDeferred = async { repository.getWorkflowRun(owner, repo, runId) }
                val jobsDeferred = async { repository.getWorkflowRunJobs(owner, repo, runId) }
                val artifactsDeferred = async { repository.getRunArtifacts(owner, repo, runId) }

                val runResult = runDeferred.await()
                _uiState.value = _uiState.value.copy(
                    run = runResult.getOrNull(),
                    jobs = jobsDeferred.await().getOrDefault(emptyList()),
                    artifacts = artifactsDeferred.await().getOrDefault(emptyList()),
                    isLoading = false,
                    error = runResult.exceptionOrNull()?.message
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * 产物走系统 DownloadManager 下载：
     * archive_download_url 需要认证，用 addRequestHeader 带 Authorization；
     * GitHub 会 302 重定向到带签名的下载 URL（无需再带 header），DownloadManager 可正常跟随。
     * 进度由系统通知栏展示，无需 App 内转圈。
     */
    fun downloadArtifact(artifact: Artifact) {
        val token = tokenManager.getToken()
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(downloadMessage = "未登录，无法下载产物") }
            return
        }
        val request = DownloadManager.Request(Uri.parse(artifact.archiveDownloadUrl))
            .setTitle(artifact.name)
            .setDescription("GitHub 产物下载")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("Authorization", "token $token")
            .addRequestHeader("Accept", "application/vnd.github+json")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${artifact.name}.zip")
        } else {
            request.setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, "${artifact.name}.zip")
        }
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager != null) {
            manager.enqueue(request)
            _uiState.update { it.copy(downloadMessage = "已开始下载 ${artifact.name}（系统通知栏可查看进度）") }
        } else {
            _uiState.update { it.copy(downloadMessage = "系统下载服务不可用") }
        }
    }
}
