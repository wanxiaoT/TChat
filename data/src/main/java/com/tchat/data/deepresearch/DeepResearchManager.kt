package com.tchat.data.deepresearch

import com.tchat.data.deepresearch.model.*
import com.tchat.data.deepresearch.repository.DeepResearchHistory
import com.tchat.data.deepresearch.repository.DeepResearchHistoryRepository
import com.tchat.data.deepresearch.repository.DeepResearchRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * 研究节点（用于树状可视化）
 */
data class ResearchNode(
    val id: String,
    val parentId: String?,
    val query: String?,
    val researchGoal: String?,
    val status: NodeStatus,
    val learnings: List<Learning> = emptyList(),
    val searchResults: List<WebSearchResult> = emptyList(),
    val errorMessage: String? = null
)

enum class NodeStatus {
    PENDING,
    GENERATING_QUERY,
    SEARCHING,
    PROCESSING,
    COMPLETE,
    ERROR
}

/**
 * 研究状态
 */
sealed class ResearchState {
    data object Idle : ResearchState()
    data object Researching : ResearchState()
    data object GeneratingReport : ResearchState()
    data class Complete(val learnings: List<Learning>, val report: String?) : ResearchState()
    data class Error(val message: String) : ResearchState()
}

/**
 * 深度研究会话
 */
data class ResearchSession(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val startTime: Long = System.currentTimeMillis(),
    val state: ResearchState = ResearchState.Idle,
    val nodes: Map<String, ResearchNode> = emptyMap(),
    val learnings: List<Learning> = emptyList(),
    val report: String = ""
)

/**
 * 深度研究管理器 - 单例
 * 管理研究任务，支持后台运行
 */
object DeepResearchManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 历史记录 Repository
    private var historyRepository: DeepResearchHistoryRepository? = null

    // 当前研究会话
    private val _currentSession = MutableStateFlow<ResearchSession?>(null)
    val currentSession: StateFlow<ResearchSession?> = _currentSession.asStateFlow()

    // 历史会话（可选，用于查看历史）
    private val _sessions = MutableStateFlow<List<ResearchSession>>(emptyList())
    val sessions: StateFlow<List<ResearchSession>> = _sessions.asStateFlow()

    // 当前研究任务
    private var currentJob: Job? = null

    /**
     * 初始化历史记录 Repository
     */
    fun init(historyRepository: DeepResearchHistoryRepository) {
        this.historyRepository = historyRepository
    }

    /**
     * 从历史记录加载会话
     */
    fun loadFromHistory(history: DeepResearchHistory) {
        // 取消当前研究
        currentJob?.cancel()
        currentJob = null

        // 创建一个已完成的会话
        val session = ResearchSession(
            id = history.id,
            query = history.query,
            startTime = history.startTime,
            state = ResearchState.Complete(history.learnings, history.report),
            nodes = emptyMap(),
            learnings = history.learnings,
            report = history.report
        )
        _currentSession.value = session
    }

    /**
     * 开始新的研究
     */
    fun startResearch(
        query: String,
        repository: DeepResearchRepository,
        config: DeepResearchConfig
    ) {
        // 取消之前的研究
        currentJob?.cancel()

        // 创建新会话
        val session = ResearchSession(
            query = query,
            state = ResearchState.Researching,
            nodes = mapOf(
                "0" to ResearchNode(
                    id = "0",
                    parentId = null,
                    query = query,
                    researchGoal = null,
                    status = NodeStatus.GENERATING_QUERY
                )
            )
        )
        _currentSession.value = session

        // 开始研究
        currentJob = scope.launch {
            try {
                repository.researchWithReport(query, config).collect { step ->
                    handleResearchStep(step)
                }
            } catch (e: CancellationException) {
                // 用户取消，不需要处理
                throw e
            } catch (e: Exception) {
                updateSession { it.copy(state = ResearchState.Error(e.message ?: "未知错误")) }
            }
        }
    }

    /**
     * 取消当前研究
     */
    fun cancelResearch() {
        currentJob?.cancel()
        currentJob = null
        updateSession { it.copy(state = ResearchState.Idle) }
    }

    /**
     * 清除当前会话
     */
    fun clearSession() {
        currentJob?.cancel()
        currentJob = null

        // 保存到历史
        _currentSession.value?.let { session ->
            if (session.state is ResearchState.Complete) {
                _sessions.value = listOf(session) + _sessions.value.take(9)
            }
        }

        _currentSession.value = null
    }

    /**
     * 处理研究进度
     */
    private fun handleResearchStep(step: ResearchStep) {
        when (step) {
            is ResearchStep.GeneratingQuery -> {
                val query = step.query
                if (query != null) {
                    updateNode(step.nodeId) { node ->
                        node.copy(
                            query = query.query,
                            researchGoal = query.researchGoal,
                            status = NodeStatus.GENERATING_QUERY
                        )
                    }
                } else {
                    updateNode(step.nodeId) { node ->
                        node.copy(status = NodeStatus.GENERATING_QUERY)
                    }
                }
                // 创建父节点关系
                if (step.parentNodeId != null && step.nodeId != step.parentNodeId) {
                    ensureNodeExists(step.nodeId, step.parentNodeId)
                }
            }

            is ResearchStep.GeneratingQueryReasoning -> {
                // 可以用于显示思维链
            }

            is ResearchStep.GeneratedQuery -> {
                updateNode(step.nodeId) { node ->
                    node.copy(
                        query = step.query.query,
                        researchGoal = step.query.researchGoal,
                        status = NodeStatus.PENDING
                    )
                }
            }

            is ResearchStep.Searching -> {
                updateNode(step.nodeId) { node ->
                    node.copy(
                        query = step.query,
                        status = NodeStatus.SEARCHING
                    )
                }
            }

            is ResearchStep.SearchComplete -> {
                updateNode(step.nodeId) { node ->
                    node.copy(
                        searchResults = step.results,
                        status = NodeStatus.PROCESSING
                    )
                }
            }

            is ResearchStep.ProcessingResult -> {
                val result = step.result
                if (result != null) {
                    updateNode(step.nodeId) { node ->
                        node.copy(
                            learnings = result.learnings,
                            status = NodeStatus.PROCESSING
                        )
                    }
                }
            }

            is ResearchStep.ProcessingResultReasoning -> {
                // 可以用于显示思维链
            }

            is ResearchStep.NodeComplete -> {
                val result = step.result
                updateNode(step.nodeId) { node ->
                    node.copy(
                        learnings = result?.learnings ?: node.learnings,
                        status = NodeStatus.COMPLETE
                    )
                }
            }

            is ResearchStep.Error -> {
                updateNode(step.nodeId) { node ->
                    node.copy(
                        status = NodeStatus.ERROR,
                        errorMessage = step.message
                    )
                }
            }

            is ResearchStep.Complete -> {
                updateSession { it.copy(
                    learnings = step.learnings,
                    state = ResearchState.GeneratingReport
                )}
            }

            is ResearchStep.GeneratingReport -> {
                updateSession { it.copy(
                    report = it.report + step.delta
                )}
            }

            is ResearchStep.ReportComplete -> {
                updateSession { it.copy(
                    report = step.report,
                    state = ResearchState.Complete(it.learnings, step.report)
                )}
                // 保存到历史记录
                saveCurrentSessionToHistory()
            }
        }
    }

    /**
     * 保存当前会话到历史记录
     */
    private fun saveCurrentSessionToHistory() {
        val session = _currentSession.value ?: return
        val repo = historyRepository ?: return

        scope.launch {
            try {
                val history = DeepResearchHistory(
                    id = session.id,
                    query = session.query,
                    report = session.report,
                    learnings = session.learnings,
                    startTime = session.startTime,
                    endTime = System.currentTimeMillis(),
                    status = "complete"
                )
                repo.saveHistory(history)
            } catch (e: Exception) {
                // 保存失败不影响主流程
                e.printStackTrace()
            }
        }
    }

    private fun ensureNodeExists(nodeId: String, parentId: String?) {
        updateSession { session ->
            if (!session.nodes.containsKey(nodeId)) {
                session.copy(
                    nodes = session.nodes + (nodeId to ResearchNode(
                        id = nodeId,
                        parentId = parentId,
                        query = null,
                        researchGoal = null,
                        status = NodeStatus.PENDING
                    ))
                )
            } else {
                session
            }
        }
    }

    private fun updateNode(nodeId: String, update: (ResearchNode) -> ResearchNode) {
        updateSession { session ->
            val currentNodes = session.nodes.toMutableMap()
            val node = currentNodes[nodeId] ?: ResearchNode(
                id = nodeId,
                parentId = nodeId.substringBeforeLast("-", "0").takeIf { it != nodeId },
                query = null,
                researchGoal = null,
                status = NodeStatus.PENDING
            )
            currentNodes[nodeId] = update(node)
            session.copy(nodes = currentNodes)
        }
    }

    private inline fun updateSession(update: (ResearchSession) -> ResearchSession) {
        _currentSession.value?.let { session ->
            _currentSession.value = update(session)
        }
    }
}
