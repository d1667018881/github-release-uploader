package com.github.releaseuploader.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

class RateLimitExceededException(message: String) : Exception(message)

@Singleton
class RateLimitInterceptor @Inject constructor() : Interceptor {

    private val _rateLimitExceeded = MutableStateFlow(false)
    val rateLimitExceeded: StateFlow<Boolean> = _rateLimitExceeded

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401 || response.code == 403) {
            val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull() ?: -1
            if (remaining == 0) {
                _rateLimitExceeded.value = true
                throw RateLimitExceededException("API rate limit exceeded. Please try again later.")
            }
        }

        return response
    }
}
