package com.github.releaseuploader.network

import java.io.IOException

/**
 * 携带 HTTP 状态码的 API 错误，用于区分 4xx/5xx 决定是否重试。
 * 4xx（含 422 校验错误、401 鉴权失败）重试无意义，5xx 与网络层 IOException 才值得重试。
 */
class ApiException(val code: Int, message: String) : IOException(message)
