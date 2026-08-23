package com.github.releaseuploader.network

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

class ProgressRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val fileName: String
) : RequestBody() {

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _fileName = MutableStateFlow(fileName)
    val fileNameFlow: StateFlow<String> = _fileName

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long {
        return contentResolver.openInputStream(uri)?.use { stream ->
            stream.available().toLong()
        } ?: -1L
    }

    override fun writeTo(sink: BufferedSink) {
        val totalBytes = contentLength()
        if (totalBytes <= 0) {
            sink.writeUtf8("")
            return
        }

        contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var uploadedBytes = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                sink.write(buffer, 0, bytesRead)
                uploadedBytes += bytesRead
                _progress.value = (uploadedBytes.toFloat() / totalBytes.toFloat()) * 100f
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
