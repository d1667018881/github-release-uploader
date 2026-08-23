package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.model.ContentItem
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoDetailUiState(
    val contents: List<ContentItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPath: String = "",
    val releaseTag: String = "",
    val showReleaseDialog: Boolean = false,
    val isCreatingRelease: Boolean = false,
    val uploadUrl: String = ""
)

@HiltViewModel
class RepoDetailViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoDetailUiState())
    val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

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

    fun showReleaseDialog() {
        _uiState.value = _uiState.value.copy(showReleaseDialog = true)
    }

    fun hideReleaseDialog() {
        _uiState.value = _uiState.value.copy(showReleaseDialog = false)
    }

    fun setReleaseTag(tag: String) {
        _uiState.value = _uiState.value.copy(releaseTag = tag)
    }

    fun createRelease(owner: String, repo: String, onSuccess: (String) -> Unit) {
        val tag = _uiState.value.releaseTag
        if (tag.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingRelease = true)
            repository.createRelease(owner, repo, tag).fold(
                onSuccess = { release ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingRelease = false,
                        showReleaseDialog = false,
                        uploadUrl = release.uploadUrl
                    )
                    onSuccess(release.uploadUrl)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingRelease = false,
                        error = "Failed to create release: ${e.message}"
                    )
                }
            )
        }
    }
}
