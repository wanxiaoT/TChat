package com.tchat.wanxiaot.ui.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.model.McpServer
import com.tchat.data.model.McpToolDefinition
import com.tchat.data.repository.McpServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MCP 服务器管理 ViewModel
 */
class McpViewModel(
    private val repository: McpServerRepository
) : ViewModel() {

    private val _servers = MutableStateFlow<List<McpServer>>(emptyList())
    val servers: StateFlow<List<McpServer>> = _servers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    init {
        loadServers()
    }

    private fun loadServers() {
        viewModelScope.launch {
            repository.getAllServers().collect { serverList ->
                _servers.value = serverList
            }
        }
    }

    fun addServer(server: McpServer) {
        viewModelScope.launch {
            repository.addServer(server)
        }
    }

    fun updateServer(server: McpServer) {
        viewModelScope.launch {
            repository.updateServer(server)
        }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch {
            repository.deleteServer(id)
        }
    }

    fun testConnection(server: McpServer) {
        viewModelScope.launch {
            _isLoading.value = true
            _testResult.value = null

            val result = repository.testConnection(server)
            _testResult.value = if (result.isSuccess) {
                TestResult.Success(result.getOrThrow())
            } else {
                TestResult.Error(result.exceptionOrNull()?.message ?: "连接失败")
            }

            _isLoading.value = false
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    sealed class TestResult {
        data class Success(val tools: List<McpToolDefinition>) : TestResult()
        data class Error(val message: String) : TestResult()
    }
}
