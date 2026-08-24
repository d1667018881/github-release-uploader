package com.github.releaseuploader.ui.viewmodel

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.model.ContentItem
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CodeBrowserUiState(
    val content: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val fileName: String = "",
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class CodeBrowserViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeBrowserUiState())
    val uiState: StateFlow<CodeBrowserUiState> = _uiState.asStateFlow()

    init {
        // 限流登出时同步响应，导航回登录页
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
            }
        }
    }

    fun loadFile(owner: String, repo: String, path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                fileName = path.substringAfterLast("/")
            )
            // 网络请求 + Base64 解码整体放 IO 线程，避免主线程解码 ~1.3MB 内容卡顿/ANR
            val result = withContext(Dispatchers.IO) {
                repository.getFileContent(owner, repo, path).map { item -> decodeContent(item) }
            }
            result.fold(
                onSuccess = { decoded ->
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

    private fun decodeContent(item: ContentItem): String {
        if (item.encoding == "base64" && item.content != null) {
            return try {
                val cleanContent = item.content.replace("\n", "").replace("\r", "")
                // 显式 UTF-8 解码，避免非 UTF-8 设备上乱码
                String(Base64.decode(cleanContent, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                "Error decoding file content: ${e.message}"
            }
        }
        return item.content ?: ""
    }
}
