package com.github.releaseuploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.releaseuploader.data.local.SessionManager
import com.github.releaseuploader.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StepLogsUiState(
    /** 当前步骤日志按行拆分（\n），UI 逐行渲染 */
    val lines: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class StepLogsViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StepLogsUiState())
    val uiState: StateFlow<StepLogsUiState> = _uiState.asStateFlow()

    // O1：同一 job 的日志全文按 jobId 缓存，切换步骤只重新 extract，不重复下载
    private val logCache = mutableMapOf<Long, String>()

    init {
        viewModelScope.launch {
            sessionManager.loggedOut.collect {
                _uiState.value = _uiState.value.copy(isLoggedOut = true)
            }
        }
    }

    fun loadStepLogs(owner: String, repo: String, jobId: Long, stepNumber: Int, stepName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val cached = logCache[jobId]
            if (cached != null) {
                applyLog(cached, stepNumber, stepName)
                return@launch
            }
            repository.getJobLogs(owner, repo, jobId).fold(
                onSuccess = { log ->
                    logCache[jobId] = log
                    applyLog(log, stepNumber, stepName)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    private suspend fun applyLog(log: String, stepNumber: Int, stepName: String) {
        // N3：日志解析（extractStepLog 状态机 + split + filter）在 Default 线程，避免 MB 级日志卡主线程
        val lines = withContext(Dispatchers.Default) {
            val block = extractStepLog(log, stepNumber, stepName)
            block.split("\n").filter { it.isNotBlank() }
        }
        _uiState.value = _uiState.value.copy(lines = lines, isLoading = false)
    }

    companion object {
        /**
         * 从完整 job 日志中提取指定步骤的日志段。
         * GitHub 日志用 `##[group]<步骤名>` 和 `##[endgroup]` 标记每个步骤的日志块。
         * 匹配策略：①块标题与步骤名精确匹配；②按步骤序号（number-1）取块；③整段兜底。
         */
        fun extractStepLog(log: String, stepNumber: Int, stepName: String): String {
            val groupRegex = Regex("##\\[group\\](.*)")
            val blocks = mutableListOf<Pair<String, String>>()
            var currentTitle: String? = null
            val currentContent = StringBuilder()
            var inGroup = false

            for (rawLine in log.split("\n")) {
                val line = rawLine.trimEnd('\r')
                val groupMatch = groupRegex.find(line)
                when {
                    groupMatch != null -> {
                        if (currentTitle != null) {
                            blocks.add(currentTitle to currentContent.toString())
                        }
                        currentTitle = groupMatch.groupValues[1].trim()
                        currentContent.clear()
                        inGroup = true
                    }
                    line.trim() == "##[endgroup]" -> {
                        if (currentTitle != null) {
                            blocks.add(currentTitle to currentContent.toString())
                            currentTitle = null
                            currentContent.clear()
                        }
                        inGroup = false
                    }
                    inGroup && currentTitle != null -> {
                        currentContent.append(line).append('\n')
                    }
                }
            }
            if (currentTitle != null) {
                blocks.add(currentTitle to currentContent.toString())
            }

            // ① 标题精确匹配（忽略首尾空白）
            blocks.firstOrNull { it.first == stepName }?.second?.let { return it }
            // ② 按步骤序号（number 从 1 开始）
            if (stepNumber - 1 in blocks.indices) return blocks[stepNumber - 1].second
            // ③ 兜底：整段日志
            return log
        }
    }
}
