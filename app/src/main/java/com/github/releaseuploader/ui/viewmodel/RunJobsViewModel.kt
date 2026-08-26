package com.github.releaseuploader.ui.viewmodel

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.model.Artifact
import com.github.releaseuploader.data.model.WorkflowRun
import com.github.releaseuploader.data.model.WorkflowRunJob
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class RunJobsUiState(
    val run: WorkflowRun? = null,
    val jobs: List<WorkflowRunJob> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDownloading: Boolean = false,
    val downloadMessage: String? = null,
    val isLoggedOut: Boolean = false
)

/** 运行详情页：run 概要（状态/耗时/触发）+ 产物 + job 列表 */
@HiltViewModel
class RunJobsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: GitHubRepository,
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

    /** 下载产物 zip（archive_download_url 需认证，走 AuthInterceptor；流式写文件） */
    fun downloadArtifact(artifact: Artifact) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDownloading = true, downloadMessage = null) }
            repository.downloadArtifactStream(artifact.archiveDownloadUrl).fold(
                onSuccess = { body ->
                    try {
                        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        } else {
                            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        }
                        dir?.mkdirs()
                        val file = File(dir, "${artifact.name}.zip")
                        body.use { resp ->
                            resp.byteStream().use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        _uiState.update {
                            it.copy(isDownloading = false, downloadMessage = "已下载：${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(isDownloading = false, downloadMessage = "下载失败：${e.message}")
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isDownloading = false, downloadMessage = "下载失败：${e.message}")
                    }
                }
            )
        }
    }
}
