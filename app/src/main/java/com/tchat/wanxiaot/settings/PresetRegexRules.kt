package com.tchat.wanxiaot.settings

/**
 * 预设正则表达式规则
 * 提供常用的 AI 输出清理规则模板
 */
object PresetRegexRules {

    val rules = listOf(
        RegexRule(
            id = "preset_1",
            name = "清除行首空格",
            pattern = "^ +",
            replacement = "",
            isEnabled = true,
            description = "移除每行开头的多余空格",
            order = 1
        ),
        RegexRule(
            id = "preset_2",
            name = "清除行首空行",
            pattern = "^\\s*\\r?\\n",
            replacement = "",
            isEnabled = true,
            description = "移除文本开头的空行",
            order = 2
        ),
        RegexRule(
            id = "preset_3",
            name = "统一分隔符数量",
            pattern = "-{4,}",
            replacement = "---",
            isEnabled = true,
            description = "将连续4个及以上的短横线统一为3个",
            order = 3
        ),
        RegexRule(
            id = "preset_4",
            name = "分隔符后加空行",
            pattern = "(\\r?\\n)(-{3,})(\\r?\\n)(?!\\s*\\r?\\n)",
            replacement = "$1$2$3$3",
            isEnabled = true,
            description = "在 Markdown 分隔符后添加空行以确保正确渲染",
            order = 4
        ),
        RegexRule(
            id = "preset_5",
            name = "替换中文引号",
            pattern = "「([^」]*)」",
            replacement = "\"$1\"",
            isEnabled = true,
            description = "将「」替换为英文双引号",
            order = 5
        ),
        RegexRule(
            id = "preset_6",
            name = "清除 GLM 前缀标记",
            pattern = "<\\|begin_of_box\\|>",
            replacement = "",
            isEnabled = true,
            description = "清除 GLM-4.6 模型输出的前缀标记",
            order = 6
        ),
        RegexRule(
            id = "preset_7",
            name = "清除 GLM 后缀标记",
            pattern = "<\\|end_of_box\\|>",
            replacement = "",
            isEnabled = true,
            description = "清除 GLM-4.6 模型输出的后缀标记",
            order = 7
        ),
        RegexRule(
            id = "preset_8",
            name = "清除 GLM 边界标记",
            pattern = "\\|\\|",
            replacement = "",
            isEnabled = true,
            description = "清除 GLM-4.6 模型输出的边界标记",
            order = 8
        )
    )

    /**
     * 根据 ID 获取预设规则
     */
    fun getById(id: String): RegexRule? = rules.find { it.id == id }

    /**
     * 检查是否为预设规则 ID
     */
    fun isPresetId(id: String): Boolean = id.startsWith("preset_")
}
