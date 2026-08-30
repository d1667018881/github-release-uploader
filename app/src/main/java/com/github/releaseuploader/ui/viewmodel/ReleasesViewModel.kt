package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.model.Release
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReleasesUiState(
    val releases: List<Release> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val releaseTag: String = "",
    val showReleaseDialog: Boolean = false,
    val isCreatingRelease: Boolean = false,
    val releaseError: String? = null,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class ReleasesViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReleasesUiState())
    val uiState: StateFlow<ReleasesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
            }
        }
    }

    fun loadReleases(owner: String, repo: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getReleases(owner, repo).fold(
                onSuccess = { releases ->
                    _uiState.value = _uiState.value.copy(releases = releases, isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun showReleaseDialog() {
        _uiState.value = _uiState.value.copy(showReleaseDialog = true)
    }

    fun hideReleaseDialog() {
        _uiState.value = _uiState.value.copy(showReleaseDialog = false)
    }

    fun setReleaseTag(tag: String) {
        _uiState.value = _uiState.value.copy(releaseTag = tag)
    }

    /** 创建 Release（成功后回调 uploadUrl，由页面启动 UploadService 上传附件） */
    fun createRelease(owner: String, repo: String, onSuccess: (String) -> Unit) {
        val tag = _uiState.value.releaseTag
        if (tag.isBlank()) {
            _uiState.update { it.copy(releaseError = "标签不能为空") }
            return
        }
        // GitHub tag 规则：不能以 "." 开头、不能包含 ".."、空白、~ ^ : ? * [ \
        if (tag.startsWith(".") || tag.contains("..") || tag.any { it.isWhitespace() || it in "~^:?*[\\" }) {
            _uiState.update { it.copy(releaseError = "标签格式无效：不能以 . 开头，不能包含空白字符或 ~ ^ : ? * [ \\") }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingRelease = true)
            repository.createRelease(owner, repo, tag).fold(
                onSuccess = { release ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingRelease = false,
                        showReleaseDialog = false,
                        releaseError = null
                    )
                    onSuccess(release.uploadUrl)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingRelease = false,
                        releaseError = "创建 Release 失败：${e.message}"
                    )
                }
            )
        }
    }
}
