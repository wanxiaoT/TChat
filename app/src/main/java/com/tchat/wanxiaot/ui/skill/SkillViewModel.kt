package com.tchat.wanxiaot.ui.skill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tchat.data.model.Skill
import com.tchat.data.repository.SkillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Skills 列表 ViewModel
 */
class SkillViewModel(
    private val repository: SkillRepository
) : ViewModel() {

    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadSkills()
        initBuiltInSkills()
    }

    /**
     * 加载 Skills 列表
     */
    private fun loadSkills() {
        viewModelScope.launch {
            repository.getAllSkills().collect { list ->
                _skills.value = list
            }
        }
    }

    /**
     * 初始化内置 Skills
     */
    private fun initBuiltInSkills() {
        viewModelScope.launch {
            repository.initBuiltInSkills()
        }
    }

    /**
     * 创建新 Skill
     */
    fun createSkill(skill: Skill) {
        viewModelScope.launch {
            repository.saveSkill(skill)
        }
    }

    /**
     * 更新 Skill
     */
    fun updateSkill(skill: Skill) {
        viewModelScope.launch {
            repository.saveSkill(skill.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * 删除 Skill
     */
    fun deleteSkill(id: String) {
        viewModelScope.launch {
            repository.deleteSkill(id)
        }
    }

    /**
     * 切换 Skill 启用状态
     */
    fun toggleSkillEnabled(skill: Skill) {
        viewModelScope.launch {
            repository.saveSkill(
                skill.copy(
                    enabled = !skill.enabled,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 复制 Skill
     */
    fun copySkill(skill: Skill) {
        viewModelScope.launch {
            val copy = skill.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${skill.name}-copy",
                displayName = "${skill.displayName} (副本)",
                isBuiltIn = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveSkill(copy)
        }
    }

    /**
     * 导入 Skill（支持 SKILL.md 格式和 JSON 格式）
     *
     * SKILL.md 格式示例:
     * ```
     * ---
     * name: my-skill
     * description: 当用户请求xxx时触发
     * ---
     *
     * Skill 内容...
     * ```
     *
     * JSON 格式示例:
     * ```json
     * {
     *   "name": "my-skill",
     *   "displayName": "我的技能",
     *   "description": "触发描述",
     *   "content": "技能内容",
     *   "triggerKeywords": ["关键词1", "关键词2"]
     * }
     * ```
     */
    fun importSkill(content: String): String {
        return try {
            val trimmedContent = content.trim()

            val skill = if (trimmedContent.startsWith("{")) {
                // JSON 格式
                parseJsonSkill(trimmedContent)
            } else if (trimmedContent.startsWith("---")) {
                // SKILL.md 格式 (YAML frontmatter + markdown content)
                parseMarkdownSkill(trimmedContent)
            } else {
                // 尝试作为纯文本内容导入
                parsePlainTextSkill(trimmedContent)
            }

            // 保存到数据库
            runBlocking {
                repository.saveSkill(skill)
            }

            "成功导入 Skill: ${skill.displayName}"
        } catch (e: Exception) {
            "导入失败: ${e.message}"
        }
    }

    /**
     * 解析 JSON 格式的 Skill
     */
    private fun parseJsonSkill(json: String): Skill {
        val jsonObject = org.json.JSONObject(json)

        val name = jsonObject.optString("name", "imported-skill-${System.currentTimeMillis()}")
        val displayName = jsonObject.optString("displayName", jsonObject.optString("display_name", name))
        val description = jsonObject.optString("description", "")
        val content = jsonObject.optString("content", "")
        val priority = jsonObject.optInt("priority", 0)
        val enabled = jsonObject.optBoolean("enabled", true)

        // 解析触发关键词
        val triggerKeywords = mutableListOf<String>()
        val keywordsArray = jsonObject.optJSONArray("triggerKeywords")
            ?: jsonObject.optJSONArray("trigger_keywords")
        if (keywordsArray != null) {
            for (i in 0 until keywordsArray.length()) {
                triggerKeywords.add(keywordsArray.getString(i))
            }
        }

        return Skill(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            displayName = displayName,
            description = description,
            content = content,
            triggerKeywords = triggerKeywords,
            priority = priority,
            enabled = enabled,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 解析 SKILL.md 格式（YAML frontmatter + markdown content）
     */
    private fun parseMarkdownSkill(markdown: String): Skill {
        // 分离 frontmatter 和 content
        val parts = markdown.split("---", limit = 3)
        if (parts.size < 3) {
            throw IllegalArgumentException("无效的 SKILL.md 格式：缺少 YAML frontmatter")
        }

        val frontmatter = parts[1].trim()
        val content = parts[2].trim()

        // 解析 YAML frontmatter（简单解析）
        var name = "imported-skill-${System.currentTimeMillis()}"
        var displayName = ""
        var description = ""
        val triggerKeywords = mutableListOf<String>()

        frontmatter.lines().forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim().removeSurrounding("\"").removeSurrounding("'")

                when (key.lowercase()) {
                    "name" -> name = value
                    "displayname", "display_name", "title" -> displayName = value
                    "description" -> description = value
                    "keywords", "trigger_keywords", "triggerkeywords" -> {
                        // 支持逗号分隔的关键词
                        value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                            triggerKeywords.add(it)
                        }
                    }
                }
            }
        }

        if (displayName.isEmpty()) {
            displayName = name
        }

        return Skill(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            displayName = displayName,
            description = description,
            content = content,
            triggerKeywords = triggerKeywords,
            priority = 0,
            enabled = true,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 解析纯文本格式（将整个内容作为 Skill content）
     */
    private fun parsePlainTextSkill(text: String): Skill {
        val name = "imported-skill-${System.currentTimeMillis()}"
        val firstLine = text.lines().firstOrNull()?.take(50) ?: "导入的 Skill"

        return Skill(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            displayName = firstLine,
            description = "从文件导入的 Skill",
            content = text,
            triggerKeywords = emptyList(),
            priority = 0,
            enabled = true,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
