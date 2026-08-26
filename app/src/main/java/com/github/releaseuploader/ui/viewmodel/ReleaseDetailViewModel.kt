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
import com.github.releaseuploader.data.model.Release
import com.github.releaseuploader.data.model.ReleaseAsset
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReleaseDetailUiState(
    val release: Release? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadMessage: String? = null,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class ReleaseDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReleaseDetailUiState())
    val uiState: StateFlow<ReleaseDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
            }
        }
    }

    fun loadRelease(owner: String, repo: String, releaseId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getRelease(owner, repo, releaseId).fold(
                onSuccess = { release ->
                    _uiState.value = _uiState.value.copy(release = release, isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    /**
     * 下载附件走系统 DownloadManager。
     * ⚠️ 私人仓库的 browser_download_url 需要认证（官方 App 能下是因为带 token），
     * 必须 addRequestHeader 带 Authorization，否则 DownloadManager 直接下载会 401 失败。
     */
    fun downloadAsset(asset: ReleaseAsset) {
        val token = tokenManager.getToken()
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(downloadMessage = "未登录，无法下载附件") }
            return
        }
        val request = DownloadManager.Request(Uri.parse(asset.browserDownloadUrl))
            .setTitle(asset.name)
            .setDescription("GitHub Release 附件下载")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("Authorization", "token $token")
            .addRequestHeader("Accept", "application/octet-stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
        } else {
            request.setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, asset.name)
        }
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager != null) {
            manager.enqueue(request)
            _uiState.update { it.copy(downloadMessage = "已开始下载 ${asset.name}（系统通知栏查看进度）") }
        } else {
            _uiState.update { it.copy(downloadMessage = "系统下载服务不可用") }
        }
    }
}
