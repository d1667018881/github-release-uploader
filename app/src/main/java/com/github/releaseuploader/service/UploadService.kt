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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.releaseuploader.data.repository.GitHubRepository
import com.github.releaseuploader.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        const val TAG = "UploadService"
        const val CHANNEL_ID = "upload_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_FILES = "extra_files"
        const val EXTRA_UPLOAD_URL = "extra_upload_url"
        const val ACTION_STOP = "com.github.releaseuploader.action.STOP"
        // GitHub Release 附件大小上限 2GB
        const val MAX_ASSET_SIZE_BYTES = 2L * 1024L * 1024L * 1024L

        // 供应用内进度 UI 订阅（RepoDetailScreen collect）
        private val _uploadProgress = MutableStateFlow(UploadState())
        val uploadProgress: StateFlow<UploadState> = _uploadProgress

        fun resetState() {
            _uploadProgress.value = UploadState()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 防重入：onStartCommand 可能被连续触发（用户重复发起上传），
    // 已有上传进行中时直接忽略新请求，避免两个协程并发上传互相覆盖进度
    @Volatile
    private var isUploading = false

    // 当前上传协程的 Job 引用：取消时只 cancel Job，不 cancel scope（scope 可复用）。
    // 若 cancel scope，取消后立即重传时 launch 在已取消 scope 上永不执行，上传静默卡死。
    private var uploadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 只取消上传 Job；收尾（通知、stopSelf、isUploading 复位）
            // 由协程内 catch(CancellationException) + finally(NonCancellable) 完成
            val job = uploadJob
            if (job != null && !job.isCompleted) {
                job.cancel()
            } else {
                // 无进行中的上传（终态通知残留点击 / 新实例重启）：兜底清理并清掉残留通知
                isUploading = false
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val files = intent?.getStringArrayListExtra(EXTRA_FILES) ?: emptyList()
        val uploadUrl = intent?.getStringExtra(EXTRA_UPLOAD_URL) ?: ""

        if (files.isEmpty() || uploadUrl.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (isUploading) {
            // 已在上传中，忽略新的上传请求
            return START_NOT_STICKY
        }
        isUploading = true

        // 新上传开始前重置全局进度状态，避免 UI 残留上次上传结果
        resetState()

        startForeground(NOTIFICATION_ID, createNotification(0, 0, "开始上传..."))

        _uploadProgress.value = UploadState(isUploading = true, totalFiles = files.size)

        uploadJob = serviceScope.launch {
            try {
                val contentResolver = applicationContext.contentResolver
                for ((index, file) in files.withIndex()) {
                    // 取消时抛 CancellationException（而非 break 静默退出），
                    // 交给下方 catch(CancellationException) 走取消收尾，避免部分上传误报 "上传完成！"
                    ensureActive()
                    val uri = Uri.parse(file)
                    // SAF 文档 URI 的 lastPathSegment 不是真实文件名（如 primary:Download/app.apk），
                    // 必须通过 OpenableColumns.DISPLAY_NAME 查询
                    val fileName = queryDisplayName(contentResolver, uri)
                        ?: (uri.lastPathSegment ?: "file_$index")
                    val mimeType = contentResolver.getType(uri)

                    // GitHub Release 附件上限 2GB，超限提前报错，避免白传几 GB 后失败
                    val fileSize = querySize(contentResolver, uri)
                    if (fileSize > MAX_ASSET_SIZE_BYTES) {
                        throw IOException("文件 $fileName 超过 GitHub 2GB 上传上限")
                    }

                    _uploadProgress.value = _uploadProgress.value.copy(
                        currentFile = fileName,
                        fileIndex = index + 1,
                        fileProgress = 0f
                    )
                    updateNotification(index + 1, files.size, "正在上传 $fileName（${index + 1}/${files.size}）")

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
                                updateNotification(index + 1, files.size, "正在上传 $fileName（${index + 1}/${files.size}）")
                            }
                        }
                    )

                    if (result.isFailure) {
                        throw result.exceptionOrNull() ?: IOException("上传失败：$fileName")
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
                updateNotification(files.size, files.size, "上传完成！", ongoing = false)
            } catch (e: CancellationException) {
                // 用户主动取消：正常收尾，不是失败；必须重新抛出保持结构化并发语义
                _uploadProgress.value = _uploadProgress.value.copy(
                    isUploading = false,
                    error = null
                )
                updateNotification(0, files.size, "上传已取消", ongoing = false)
                throw e
            } catch (e: Exception) {
                _uploadProgress.value = _uploadProgress.value.copy(
                    isUploading = false,
                    error = e.message
                )
                updateNotification(0, files.size, "上传失败：${e.message}", ongoing = false)
            } finally {
                // NonCancellable 保证协程被取消时清理逻辑仍执行（普通 finally 中的挂起点会被跳过）
                withContext(NonCancellable) {
                    // 让终态通知（非 ongoing，可清除）保留在通知栏，1.5s 后停止前台服务
                    delay(1500)
                    isUploading = false
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
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
            Log.w(TAG, "queryDisplayName failed for $uri", e)
            null
        }
    }

    /** 查询文件大小（字节）；查询失败返回 -1（不拦截上传） */
    private fun querySize(contentResolver: ContentResolver, uri: Uri): Long {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else -1L
            } ?: -1L
        } catch (e: Exception) {
            Log.w(TAG, "querySize failed for $uri", e)
            -1L
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
                "上传进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示上传进度"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(current: Int, total: Int, message: String, ongoing: Boolean = true) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("上传 Release 附件")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setProgress(total, current, total == 0)
            .setContentIntent(createPendingIntent())
            .apply {
                // 仅上传中的 ongoing 通知提供 Cancel 按钮；终态通知没有取消入口，语义更干净
                if (ongoing) {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", createCancelPendingIntent())
                }
            }
            .build()

    private fun createCancelPendingIntent(): PendingIntent {
        val intent = Intent(this, UploadService::class.java).apply { action = ACTION_STOP }
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification(current: Int, total: Int, message: String, ongoing: Boolean = true) {
        val notification = createNotification(current, total, message, ongoing)
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
