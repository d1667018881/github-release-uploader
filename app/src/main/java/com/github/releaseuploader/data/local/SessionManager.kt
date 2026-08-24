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

/** 登出原因：限流触发 vs 手动登出，UI 据此决定是否提示错误 */
enum class LogoutReason { RATE_LIMIT, MANUAL }

/**
 * 会话管理单例：统一收敛「限流 → 登出」逻辑。
 * 之前 LoginViewModel / RepoListViewModel 各自 collect 限流状态并实现登出，逻辑重复且脆弱。
 * 现在由本类唯一负责：清 Token + 清缓存 + 广播登出事件，UI 层只响应事件。
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenManager: TokenManager,
    private val repository: GitHubRepository,
    private val rateLimitInterceptor: RateLimitInterceptor
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 事件型（非状态型）：登出事件不重放，避免限流后新建页面一 collect 就误登出；
    // 携带原因，区分限流登出（需提示）与手动登出（正常操作，不提示错误）。
    // extraBufferCapacity=4：订阅者忙时 tryEmit 不会静默丢事件（默认 0 buffer 会丢）
    private val _loggedOut = MutableSharedFlow<LogoutReason>(extraBufferCapacity = 4)
    val loggedOut: SharedFlow<LogoutReason> = _loggedOut.asSharedFlow()

    init {
        rateLimitInterceptor.rateLimitExceeded
            .onEach { exceeded ->
                if (exceeded) {
                    forceLogout(LogoutReason.RATE_LIMIT)
                }
            }
            .launchIn(scope)
    }

    /** 统一登出入口：手动登出也走这里，保证清缓存逻辑一致 */
    fun logout() {
        forceLogout(LogoutReason.MANUAL)
    }

    private fun forceLogout(reason: LogoutReason) {
        tokenManager.clearAll()
        repository.clearCache()
        // 登出后复位限流状态，避免残留 true（如用户不再重新登录直接退出应用）
        rateLimitInterceptor.reset()
        _loggedOut.tryEmit(reason)
    }
}
