package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.model.ContentItem
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoFilesUiState(
    val contents: List<ContentItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPath: String = "",
    val isLoggedOut: Boolean = false
)

/** 仓库文件浏览页：目录导航 + 代码查看 */
@HiltViewModel
class RepoFilesViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoFilesUiState())
    val uiState: StateFlow<RepoFilesUiState> = _uiState.asStateFlow()

    init {
        // 限流登出时同步响应，导航回登录页
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
            }
        }
    }

    fun loadContents(owner: String, repo: String, path: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentPath = path)
            repository.getContents(owner, repo, path).fold(
                onSuccess = { contents ->
                    _uiState.value = _uiState.value.copy(
                        contents = contents,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }
}
