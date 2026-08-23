package com.github.releaseuploader.data.local

import com.github.releaseuploader.data.repository.GitHubRepository
import com.github.releaseuploader.network.RateLimitInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话管理单例：统一收敛「限流 → 登出」逻辑。
 * 之前 LoginViewModel / RepoListViewModel 各自 collect 限流状态并实现登出，逻辑重复且脆弱。
 * 现在由本类唯一负责：清 Token + 清缓存 + 广播登出事件，UI 层只响应事件。
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenManager: TokenManager,
    private val repository: GitHubRepository,
    rateLimitInterceptor: RateLimitInterceptor
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 事件型（非状态型）：登出事件不重放，避免限流后新建页面一 collect 就误登出
    private val _loggedOut = MutableSharedFlow<Unit>()
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    init {
        rateLimitInterceptor.rateLimitExceeded
            .onEach { exceeded ->
                if (exceeded) {
                    forceLogout()
                }
            }
            .launchIn(scope)
    }

    /** 统一登出入口：手动登出也走这里，保证清缓存逻辑一致 */
    fun logout() {
        forceLogout()
    }

    private fun forceLogout() {
        tokenManager.clearAll()
        repository.clearCache()
        _loggedOut.tryEmit(Unit)
    }
}
