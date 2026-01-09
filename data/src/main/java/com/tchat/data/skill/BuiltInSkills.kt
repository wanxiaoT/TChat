package com.tchat.data.skill

import com.tchat.data.model.Skill

/**
 * 内置技能定义
 */
object BuiltInSkills {

    /**
     * 编程助手技能
     */
    val CODING_ASSISTANT = Skill(
        id = "builtin-coding-assistant",
        name = "coding-assistant",
        displayName = "编程助手",
        description = "当用户请求编写代码、调试程序、解释代码、代码审查时触发",
        triggerKeywords = listOf(
            "写代码", "编程", "调试", "代码", "函数", "bug", "报错",
            "编译", "运行", "程序", "算法", "实现", "开发",
            "code", "debug", "programming", "function", "error"
        ),
        content = """
作为编程助手，遵循以下原则：

1. **代码质量**
   - 代码简洁清晰，添加必要注释
   - 遵循最佳实践和设计模式
   - 考虑边界情况和错误处理

2. **解释说明**
   - 解释代码逻辑和设计决策
   - 提供示例和使用方法
   - 说明潜在的问题和改进方向

3. **调试帮助**
   - 分析错误信息和堆栈跟踪
   - 提供可能的原因和解决方案
   - 建议调试步骤和工具
        """.trimIndent(),
        priority = 10,
        isBuiltIn = true
    )

    /**
     * 写作助手技能
     */
    val WRITING_ASSISTANT = Skill(
        id = "builtin-writing-assistant",
        name = "writing-assistant",
        displayName = "写作助手",
        description = "当用户请求写文章、润色文字、翻译内容时触发",
        triggerKeywords = listOf(
            "写文章", "写作", "润色", "修改", "翻译", "文案",
            "文章", "段落", "句子", "语法", "表达",
            "write", "article", "translate", "polish"
        ),
        content = """
作为写作助手，遵循以下原则：

1. **内容创作**
   - 根据主题和要求创作内容
   - 保持逻辑清晰、结构完整
   - 使用恰当的语言风格

2. **文字润色**
   - 改进表达方式和用词
   - 修正语法和标点错误
   - 保持原意的同时提升可读性

3. **翻译服务**
   - 准确传达原文含义
   - 符合目标语言的表达习惯
   - 保持专业术语的准确性
        """.trimIndent(),
        priority = 8,
        isBuiltIn = true
    )

    /**
     * 数据分析技能
     */
    val DATA_ANALYSIS = Skill(
        id = "builtin-data-analysis",
        name = "data-analysis",
        displayName = "数据分析",
        description = "当用户请求分析数据、统计计算、数据可视化时触发",
        triggerKeywords = listOf(
            "数据分析", "统计", "分析", "数据", "图表",
            "计算", "平均", "趋势", "报表", "Excel",
            "data", "analysis", "statistics", "chart"
        ),
        content = """
作为数据分析助手，遵循以下原则：

1. **数据理解**
   - 理解数据结构和含义
   - 识别数据质量问题
   - 确定分析目标和方法

2. **分析方法**
   - 选择合适的统计方法
   - 进行数据清洗和预处理
   - 提供准确的计算结果

3. **结果呈现**
   - 清晰解释分析结果
   - 提供可视化建议
   - 给出可行的建议和结论
        """.trimIndent(),
        priority = 7,
        isBuiltIn = true
    )

    /**
     * 学习辅导技能
     */
    val LEARNING_TUTOR = Skill(
        id = "builtin-learning-tutor",
        name = "learning-tutor",
        displayName = "学习辅导",
        description = "当用户请求学习帮助、解释概念、答疑解惑时触发",
        triggerKeywords = listOf(
            "学习", "教我", "解释", "什么是", "为什么",
            "怎么理解", "概念", "原理", "知识", "题目",
            "learn", "explain", "what is", "why", "how"
        ),
        content = """
作为学习辅导助手，遵循以下原则：

1. **概念解释**
   - 用简单易懂的语言解释
   - 提供具体的例子和类比
   - 循序渐进，由浅入深

2. **问题解答**
   - 分析问题的关键点
   - 提供解题思路和步骤
   - 鼓励独立思考

3. **知识拓展**
   - 关联相关知识点
   - 推荐学习资源
   - 提供练习建议
        """.trimIndent(),
        priority = 6,
        isBuiltIn = true
    )

    /**
     * 创意助手技能
     */
    val CREATIVE_ASSISTANT = Skill(
        id = "builtin-creative-assistant",
        name = "creative-assistant",
        displayName = "创意助手",
        description = "当用户请求头脑风暴、创意想法、设计建议时触发",
        triggerKeywords = listOf(
            "创意", "想法", "点子", "灵感", "设计",
            "头脑风暴", "建议", "方案", "策划",
            "idea", "creative", "brainstorm", "design"
        ),
        content = """
作为创意助手，遵循以下原则：

1. **创意激发**
   - 提供多角度的思考方向
   - 打破常规思维模式
   - 结合不同领域的灵感

2. **方案设计**
   - 提供具体可行的方案
   - 考虑实施的可行性
   - 评估优缺点和风险

3. **优化建议**
   - 在现有基础上改进
   - 提供差异化的选择
   - 帮助筛选最佳方案
        """.trimIndent(),
        priority = 5,
        isBuiltIn = true
    )

    /**
     * 获取所有内置技能
     */
    fun getAll(): List<Skill> = listOf(
        CODING_ASSISTANT,
        WRITING_ASSISTANT,
        DATA_ANALYSIS,
        LEARNING_TUTOR,
        CREATIVE_ASSISTANT
    )

    /**
     * 根据名称获取内置技能
     */
    fun getByName(name: String): Skill? = getAll().find { it.name == name }
}
