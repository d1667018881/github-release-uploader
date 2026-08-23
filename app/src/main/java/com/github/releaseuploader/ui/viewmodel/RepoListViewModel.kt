package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.TokenManager
import com.github.releaseuploader.data.model.Repo
import com.github.releaseuploader.data.repository.GitHubRepository
import com.github.releaseuploader.network.RateLimitInterceptor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoListUiState(
    val repos: List<Repo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class RepoListViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager,
    private val rateLimitInterceptor: RateLimitInterceptor
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoListUiState())
    val uiState: StateFlow<RepoListUiState> = _uiState.asStateFlow()

    init {
        loadRepos()
        viewModelScope.launch {
            rateLimitInterceptor.rateLimitExceeded.collect { exceeded ->
                if (exceeded) {
                    tokenManager.clearAll()
                    _uiState.value = _uiState.value.copy(isLoggedOut = true)
                }
            }
        }
    }

    fun loadRepos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getUserRepos(1).fold(
                onSuccess = { repos ->
                    _uiState.value = _uiState.value.copy(
                        repos = repos,
                        isLoading = false,
                        hasMore = repos.size >= 30,
                        currentPage = 1
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

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            val nextPage = state.currentPage + 1
            repository.getUserRepos(nextPage).fold(
                onSuccess = { repos ->
                    _uiState.value = _uiState.value.copy(
                        repos = _uiState.value.repos + repos,
                        isLoadingMore = false,
                        hasMore = repos.size >= 30,
                        currentPage = nextPage
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun logout() {
        tokenManager.clearAll()
        _uiState.value = RepoListUiState(isLoggedOut = true)
    }
}
