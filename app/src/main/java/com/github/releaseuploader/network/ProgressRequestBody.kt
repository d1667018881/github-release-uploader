package com.github.releaseuploader.network

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

class ProgressRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit = {}
) : RequestBody() {

    // 缓存长度：OkHttp 会多次调用 contentLength()，避免每次都开/关 FileDescriptor
    private val cachedLength: Long by lazy {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size >= 0) size else -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = cachedLength

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = cachedLength
        // openInputStream 为 null（URI 权限丢失/文件被删）必须抛异常，
        // 不能静默跳过导致 OkHttp 发送 0 字节 body 却显示上传成功
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开 URI 输入流：$uri")
        inputStream.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var uploadedBytes = 0L
            var bytesRead: Int

            while (stream.read(buffer).also { bytesRead = it } != -1) {
                sink.write(buffer, 0, bytesRead)
                uploadedBytes += bytesRead
                if (totalBytes > 0) {
                    onProgress((uploadedBytes.toFloat() / totalBytes.toFloat()) * 100f)
                }
            }
        }
    }

    companion object {
        // 64KB 缓冲：提高吞吐、减少 onProgress 回调频率（配合上层节流）
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
