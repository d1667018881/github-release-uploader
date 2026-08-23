package com.github.releaseuploader.network

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

class ProgressRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val onProgress: (Float) -> Unit = {}
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size >= 0) size else -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength()

        contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var uploadedBytes = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                sink.write(buffer, 0, bytesRead)
                uploadedBytes += bytesRead
                if (totalBytes > 0) {
                    onProgress((uploadedBytes.toFloat() / totalBytes.toFloat()) * 100f)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
