package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JobLogsUiState(
    /** 日志按行拆分（\n），UI 逐行渲染 */
    val lines: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class JobLogsViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(JobLogsUiState())
    val uiState: StateFlow<JobLogsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
            }
        }
    }

    fun loadLogs(owner: String, repo: String, jobId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getJobLogs(owner, repo, jobId).fold(
                onSuccess = { log ->
                    val lines = log.split("\n").filter { it.isNotBlank() }
                    _uiState.value = _uiState.value.copy(lines = lines, isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }
}
