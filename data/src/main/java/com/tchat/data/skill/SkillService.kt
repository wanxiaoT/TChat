package com.tchat.data.skill

import com.tchat.data.model.Skill
import com.tchat.data.model.SkillToolDefinition
import com.tchat.data.model.SkillToolExecuteType
import com.tchat.data.repository.SkillRepository
import com.tchat.data.tool.InputSchema
import com.tchat.data.tool.PropertyDef
import com.tchat.data.tool.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 技能服务
 *
 * 负责技能的激活、系统提示构建和工具转换
 */
class SkillService(
    private val skillRepository: SkillRepository,
    private val skillMatcher: SkillMatcher = SkillMatcher()
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 根据用户输入和助手配置，获取应该激活的技能
     *
     * @param userInput 用户输入
     * @param enabledSkillIds 助手启用的技能ID列表
     * @param maxSkills 最多激活的技能数量
     * @return 激活的技能列表
     */
    suspend fun getActivatedSkills(
        userInput: String,
        enabledSkillIds: List<String>,
        maxSkills: Int = 3
    ): List<Skill> {
        if (enabledSkillIds.isEmpty()) return emptyList()

        // 获取助手启用的技能
        val enabledSkills = skillRepository.getSkillsByIds(enabledSkillIds)
            .filter { it.enabled }

        if (enabledSkills.isEmpty()) return emptyList()

        // 匹配技能
        return skillMatcher.getMatchedSkills(
            userInput = userInput,
            availableSkills = enabledSkills,
            minScore = 0.5f,
            maxCount = maxSkills
        )
    }

    /**
     * 构建包含技能内容的系统提示
     *
     * @param baseSystemPrompt 基础系统提示
     * @param activatedSkills 激活的技能列表
     * @return 增强后的系统提示
     */
    fun buildSystemPromptWithSkills(
        baseSystemPrompt: String,
        activatedSkills: List<Skill>
    ): String {
        if (activatedSkills.isEmpty()) {
            return baseSystemPrompt
        }

        val skillsSection = buildString {
            appendLine()
            appendLine()
            appendLine("<activated_skills>")
            for (skill in activatedSkills) {
                appendLine("## ${skill.displayName}")
                appendLine()
                appendLine(skill.content)
                appendLine()
            }
            appendLine("</activated_skills>")
        }

        return baseSystemPrompt + skillsSection
    }

    /**
     * 获取技能定义的工具
     *
     * @param skills 技能列表
     * @return 工具列表
     */
    fun getToolsFromSkills(skills: List<Skill>): List<Tool> {
        return skills.flatMap { skill ->
            skill.tools.map { toolDef ->
                convertToTool(skill, toolDef)
            }
        }
    }

    /**
     * 将技能工具定义转换为 Tool 对象
     */
    private fun convertToTool(skill: Skill, toolDef: SkillToolDefinition): Tool {
        return Tool(
            name = "${skill.name}__${toolDef.name}",
            description = "[${skill.displayName}] ${toolDef.description}",
            parameters = { parseParametersSchema(toolDef.parametersJson) },
            execute = { args -> executeSkillTool(toolDef, args) }
        )
    }

    /**
     * 解析参数 Schema
     */
    private fun parseParametersSchema(json: String): InputSchema? {
        return try {
            val obj = JSONObject(json)
            val properties = mutableMapOf<String, PropertyDef>()
            val required = mutableListOf<String>()

            val propsObj = obj.optJSONObject("properties")
            propsObj?.keys()?.forEach { key ->
                val propObj = propsObj.getJSONObject(key)
                properties[key] = PropertyDef(
                    type = propObj.optString("type", "string"),
                    description = propObj.optString("description"),
                    enum = propObj.optJSONArray("enum")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    }
                )
            }

            obj.optJSONArray("required")?.let { arr ->
                (0 until arr.length()).forEach { i ->
                    required.add(arr.getString(i))
                }
            }

            if (properties.isNotEmpty()) {
                InputSchema.Obj(properties, required.ifEmpty { null })
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 执行技能工具
     */
    private suspend fun executeSkillTool(
        toolDef: SkillToolDefinition,
        args: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        when (toolDef.executeType) {
            SkillToolExecuteType.HTTP_REQUEST -> executeHttpRequest(toolDef.executeConfig, args)
            SkillToolExecuteType.TEMPLATE_RESPONSE -> executeTemplateResponse(toolDef.executeConfig, args)
        }
    }

    /**
     * 执行 HTTP 请求
     */
    private fun executeHttpRequest(config: String, args: JSONObject): JSONObject {
        return try {
            val configObj = JSONObject(config)
            val url = configObj.optString("url", "")
            val method = configObj.optString("method", "GET").uppercase()
            val headers = configObj.optJSONObject("headers")

            if (url.isEmpty()) {
                return JSONObject().apply {
                    put("error", "URL is required")
                }
            }

            // 替换 URL 中的参数占位符
            var finalUrl = url
            args.keys().forEach { key ->
                finalUrl = finalUrl.replace("{$key}", args.optString(key, ""))
            }

            val requestBuilder = Request.Builder().url(finalUrl)

            // 添加请求头
            headers?.keys()?.forEach { key ->
                requestBuilder.addHeader(key, headers.getString(key))
            }

            // 设置请求方法和请求体
            when (method) {
                "POST", "PUT", "PATCH" -> {
                    val body = args.toString().toRequestBody("application/json".toMediaType())
                    when (method) {
                        "POST" -> requestBuilder.post(body)
                        "PUT" -> requestBuilder.put(body)
                        "PATCH" -> requestBuilder.patch(body)
                    }
                }
                "DELETE" -> requestBuilder.delete()
                else -> requestBuilder.get()
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            JSONObject().apply {
                put("status", response.code)
                put("success", response.isSuccessful)
                try {
                    put("data", JSONObject(responseBody))
                } catch (e: Exception) {
                    put("data", responseBody)
                }
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", e.message ?: "HTTP request failed")
            }
        }
    }

    /**
     * 执行模板响应
     */
    private fun executeTemplateResponse(config: String, args: JSONObject): JSONObject {
        return try {
            val configObj = JSONObject(config)
            var template = configObj.optString("template", "")

            // 替换模板中的参数占位符
            args.keys().forEach { key ->
                template = template.replace("{$key}", args.optString(key, ""))
            }

            JSONObject().apply {
                put("result", template)
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", e.message ?: "Template execution failed")
            }
        }
    }
}
