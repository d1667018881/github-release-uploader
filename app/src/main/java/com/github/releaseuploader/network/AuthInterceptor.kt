package com.github.releaseuploader.network

import com.github.releaseuploader.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenManager.getToken()

        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "token $token")
                .header("Accept", "application/vnd.github+json")
                .build()
        } else {
            originalRequest.newBuilder()
                .header("Accept", "application/vnd.github+json")
                .build()
        }

        return chain.proceed(request)
    }
}
