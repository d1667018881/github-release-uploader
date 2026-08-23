package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val rateLimitInterceptor: RateLimitInterceptor
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        if (tokenManager.isLoggedIn()) {
            _uiState.value = LoginUiState(isLoggedIn = true)
        }
        viewModelScope.launch {
            rateLimitInterceptor.rateLimitExceeded.collect { exceeded ->
                if (exceeded) {
                    logout()
                    _uiState.value = _uiState.value.copy(
                        error = "API rate limit exceeded. You have been logged out."
                    )
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
        tokenManager.clearAll()
        _uiState.value = LoginUiState(isLoggedIn = false)
    }
}
