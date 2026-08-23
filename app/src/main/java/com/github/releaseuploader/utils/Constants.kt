package com.github.releaseuploader.utils

object Constants {
    const val GITHUB_API_BASE_URL = "https://api.github.com/"
    const val MAX_FILE_SIZE_BYTES = 1_000_000L // 1MB
    const val PER_PAGE = 30
    const val MAX_UPLOAD_RETRIES = 3
    const val MAX_CONTENTS_CACHE_SIZE = 50
    const val MAX_FILE_CACHE_SIZE = 10
}
