package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.LogoutReason
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.local.TokenManager
import com.github.releaseuploader.data.repository.GitHubRepository
import com.github.releaseuploader.network.RateLimitInterceptor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val repository: GitHubRepository,
    private val rateLimitInterceptor: RateLimitInterceptor,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        if (tokenManager.isLoggedIn()) {
            _uiState.value = LoginUiState(isLoggedIn = true)
        }
        // 限流导致的登出由 SessionManager 统一处理，这里只响应事件更新 UI
        viewModelScope.launch {
            sessionManager.loggedOut.collect { reason ->
                if (reason == LogoutReason.RATE_LIMIT) {
                    _uiState.value = LoginUiState(
                        error = "API rate limit exceeded. You have been logged out."
                    )
                } else {
                    _uiState.value = LoginUiState(isLoggedIn = false)
                }
            }
        }
    }

    fun login(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            tokenManager.saveToken(token)
            repository.getCurrentUser().fold(
                onSuccess = {
                    rateLimitInterceptor.reset()
                    _uiState.value = LoginUiState(isLoggedIn = true, isLoading = false)
                },
                onFailure = { e ->
                    tokenManager.clearAll()
                    _uiState.value = LoginUiState(
                        isLoading = false,
                        error = "Login failed: ${e.message}"
                    )
                }
            )
        }
    }

    fun logout() {
        sessionManager.logout()
        _uiState.value = LoginUiState(isLoggedIn = false)
    }
}
