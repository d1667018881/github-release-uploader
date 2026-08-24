package com.github.releaseuploader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import com.github.releaseuploader.data.repository.GitHubRepository
import com.github.releaseuploader.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import javax.inject.Inject

/**
 * 上传进度状态。定义为文件顶层 data class（而非 companion 嵌套类），
 * 供 UI 层（RepoDetailScreen）直接引用。
 */
data class UploadState(
    val isUploading: Boolean = false,
    val currentFile: String = "",
    val fileIndex: Int = 0,
    val totalFiles: Int = 0,
    val fileProgress: Float = 0f,
    val overallProgress: Float = 0f,
    val isComplete: Boolean = false,
    val error: String? = null
)

@AndroidEntryPoint
class UploadService : Service() {

    @Inject
    lateinit var repository: GitHubRepository

    companion object {
        const val CHANNEL_ID = "upload_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_FILES = "extra_files"
        const val EXTRA_UPLOAD_URL = "extra_upload_url"
        const val ACTION_STOP = "com.github.releaseuploader.action.STOP"

        // 供应用内进度 UI 订阅（RepoDetailScreen collect）
        private val _uploadProgress = MutableStateFlow(UploadState())
        val uploadProgress: StateFlow<UploadState> = _uploadProgress

        fun resetState() {
            _uploadProgress.value = UploadState()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val files = intent?.getStringArrayListExtra(EXTRA_FILES) ?: emptyList()
        val uploadUrl = intent?.getStringExtra(EXTRA_UPLOAD_URL) ?: ""

        if (files.isEmpty() || uploadUrl.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 新上传开始前重置全局进度状态，避免 UI 残留上次上传结果
        resetState()

        startForeground(NOTIFICATION_ID, createNotification(0, 0, "Starting upload..."))

        _uploadProgress.value = UploadState(isUploading = true, totalFiles = files.size)

        serviceScope.launch {
            try {
                val contentResolver = applicationContext.contentResolver
                for ((index, file) in files.withIndex()) {
                    if (!isActive) break
                    val uri = Uri.parse(file)
                    // SAF 文档 URI 的 lastPathSegment 不是真实文件名（如 primary:Download/app.apk），
                    // 必须通过 OpenableColumns.DISPLAY_NAME 查询
                    val fileName = queryDisplayName(contentResolver, uri)
                        ?: (uri.lastPathSegment ?: "file_$index")
                    val mimeType = contentResolver.getType(uri)

                    _uploadProgress.value = _uploadProgress.value.copy(
                        currentFile = fileName,
                        fileIndex = index + 1,
                        fileProgress = 0f
                    )
                    updateNotification(index + 1, files.size, "Uploading $fileName (${index + 1}/${files.size})")

                    // 进度节流：增量 ≥1% 或间隔 ≥250ms 才刷新 StateFlow 和通知，
                    // 避免大文件每 64KB 一次刷新把通知/主线程刷爆
                    var lastProgress = 0f
                    var lastEmit = 0L

                    val result = repository.uploadAssetWithRetry(
                        uploadUrl = uploadUrl,
                        contentResolver = contentResolver,
                        uri = uri,
                        fileName = fileName,
                        mimeType = mimeType,
                        onProgress = { progress ->
                            val now = System.currentTimeMillis()
                            if (progress - lastProgress >= 1f || now - lastEmit >= 250) {
                                lastProgress = progress
                                lastEmit = now
                                _uploadProgress.value = _uploadProgress.value.copy(
                                    fileProgress = progress,
                                    overallProgress = ((index.toFloat() + progress / 100f) / files.size) * 100f
                                )
                                updateNotification(index + 1, files.size, "Uploading $fileName (${index + 1}/${files.size})")
                            }
                        }
                    )

                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: IOException("Upload failed: $fileName")
                    }

                    _uploadProgress.value = _uploadProgress.value.copy(
                        fileProgress = 100f,
                        overallProgress = ((index + 1).toFloat() / files.size) * 100f
                    )
                }

                _uploadProgress.value = _uploadProgress.value.copy(
                    isComplete = true,
                    isUploading = false,
                    overallProgress = 100f
                )
                updateNotification(files.size, files.size, "Upload complete!")
            } catch (e: Exception) {
                _uploadProgress.value = _uploadProgress.value.copy(
                    isUploading = false,
                    error = e.message
                )
                updateNotification(0, files.size, "Upload failed: ${e.message}")
            } finally {
                // 终态通知展示 1.5s 后停止前台服务，避免服务常驻空转耗电
                delay(1500)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Upload Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows upload progress"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(current: Int, total: Int, message: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Uploading Release Assets")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(total, current, total == 0)
            .setContentIntent(createPendingIntent())
            .build()

    private fun updateNotification(current: Int, total: Int, message: String) {
        val notification = createNotification(current, total, message)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
