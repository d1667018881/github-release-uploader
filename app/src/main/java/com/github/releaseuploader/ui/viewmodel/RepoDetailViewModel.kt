package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.model.Contributor
import com.github.releaseuploader.data.model.Release
import com.github.releaseuploader.data.model.Repo
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoDetailUiState(
    val repo: Repo? = null,
    val releases: List<Release> = emptyList(),
    val contributors: List<Contributor> = emptyList(),
    val readme: String = "",
    val isStarred: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false
)

/** 仓库概览页：仓库信息 + 功能入口（发行版/贡献者）+ README 渲染 */
@HiltViewModel
class RepoDetailViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoDetailUiState())
    val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

    init {
        // 限流登出时同步响应，导航回登录页
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
            }
        }
    }

    fun loadDetail(owner: String, repo: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val repoDeferred = async { repository.getRepoDetail(owner, repo) }
                val releasesDeferred = async { repository.getReleases(owner, repo) }
                val contributorsDeferred = async { repository.getContributors(owner, repo) }
                val readmeDeferred = async { repository.getReadme(owner, repo) }
                val starredDeferred = async { repository.isStarred(owner, repo) }

                val repoResult = repoDeferred.await()
                _uiState.value = _uiState.value.copy(
                    repo = repoResult.getOrNull(),
                    releases = releasesDeferred.await().getOrDefault(emptyList()),
                    contributors = contributorsDeferred.await().getOrDefault(emptyList()),
                    readme = readmeDeferred.await().getOrDefault(""),
                    isStarred = starredDeferred.await(),
                    isLoading = false,
                    error = repoResult.exceptionOrNull()?.message
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /** 标星/取消标星，失败时回滚状态 */
    fun toggleStar(owner: String, repo: String) {
        viewModelScope.launch {
            val current = _uiState.value.isStarred
            _uiState.value = _uiState.value.copy(isStarred = !current)
            val result = if (current) repository.unstar(owner, repo) else repository.star(owner, repo)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(isStarred = current)
            }
        }
    }
}
