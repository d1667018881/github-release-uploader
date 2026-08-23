package com.github.releaseuploader.ui.viewmodel

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodeBrowserUiState(
    val content: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val fileName: String = ""
)

@HiltViewModel
class CodeBrowserViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeBrowserUiState())
    val uiState: StateFlow<CodeBrowserUiState> = _uiState.asStateFlow()

    fun loadFile(owner: String, repo: String, path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                fileName = path.substringAfterLast("/")
            )
            repository.getFileContent(owner, repo, path).fold(
                onSuccess = { item ->
                    val decoded = if (item.encoding == "base64" && item.content != null) {
                        try {
                            val cleanContent = item.content.replace("\n", "").replace("\r", "")
                            String(Base64.decode(cleanContent, Base64.DEFAULT))
                        } catch (e: Exception) {
                            "Error decoding file content: ${e.message}"
                        }
                    } else {
                        item.content ?: ""
                    }
                    _uiState.value = _uiState.value.copy(
                        content = decoded,
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
