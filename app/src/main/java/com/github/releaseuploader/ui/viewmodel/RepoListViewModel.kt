package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.local.TokenManager
import com.github.releaseuploader.data.model.Repo
import com.github.releaseuploader.data.repository.GitHubRepository
import com.github.releaseuploader.utils.Constants
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
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoListUiState())
    val uiState: StateFlow<RepoListUiState> = _uiState.asStateFlow()

    init {
        loadRepos()
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
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
                        hasMore = repos.size >= Constants.PER_PAGE,
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

    /**
     * 分页加载：用 update {} 原子读改写，杜绝并发竞态。
     * 快速滑动时 snapshotFlow 可能连续触发，但 update 内部会二次校验 isLoadingMore，
     * 只有一个协程能通过守卫，不会重复拉取同一页。
     */
    fun loadMore() {
        var started = false
        _uiState.update { s ->
            if (s.isLoadingMore || !s.hasMore) return@update s
            started = true
            s.copy(isLoadingMore = true)
        }
        if (!started) return

        viewModelScope.launch {
            val nextPage = _uiState.value.currentPage + 1
            repository.getUserRepos(nextPage).fold(
                onSuccess = { repos ->
                    _uiState.update { s ->
                        s.copy(
                            repos = s.repos + repos,
                            isLoadingMore = false,
                            hasMore = repos.size >= Constants.PER_PAGE,
                            currentPage = nextPage
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { s -> s.copy(isLoadingMore = false, error = e.message) }
                }
            )
        }
    }

    fun logout() {
        sessionManager.logout()
        _uiState.value = RepoListUiState(isLoggedOut = true)
    }
}
