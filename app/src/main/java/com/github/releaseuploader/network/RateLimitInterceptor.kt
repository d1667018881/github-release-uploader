package com.github.releaseuploader.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateLimitInterceptor @Inject constructor() : Interceptor {

    private val _rateLimitExceeded = MutableStateFlow(false)
    val rateLimitExceeded: StateFlow<Boolean> = _rateLimitExceeded

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        // GitHub 限流返回 403 且 X-RateLimit-Remaining=0；401 是 token 无效，不在此处理。
        // 不再 throw，让响应正常返回，由 Repository 的 Result 统一上报错误。
        if (response.code == 403) {
            val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
            if (remaining == 0) {
                _rateLimitExceeded.value = true
            }
        }

        return response
    }

    fun reset() {
        _rateLimitExceeded.value = false
    }
}
