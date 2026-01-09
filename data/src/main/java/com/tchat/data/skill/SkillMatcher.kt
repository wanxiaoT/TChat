package com.tchat.data.skill

import com.tchat.data.model.MatchedSkill
import com.tchat.data.model.Skill

/**
 * 技能匹配器
 *
 * 负责分析用户输入，匹配相关的技能
 */
class SkillMatcher {

    /**
     * 匹配用户输入，返回匹配的技能列表（按分数和优先级排序）
     *
     * @param userInput 用户输入
     * @param availableSkills 可用的技能列表
     * @return 匹配的技能列表
     */
    fun matchSkills(
        userInput: String,
        availableSkills: List<Skill>
    ): List<MatchedSkill> {
        val matches = mutableListOf<MatchedSkill>()
        val normalizedInput = userInput.lowercase().trim()

        for (skill in availableSkills) {
            if (!skill.enabled) continue

            val matchScore = calculateMatchScore(normalizedInput, skill)
            if (matchScore > 0) {
                matches.add(MatchedSkill(skill, matchScore))
            }
        }

        // 按匹配分数和优先级排序
        return matches.sortedWith(
            compareByDescending<MatchedSkill> { it.score }
                .thenByDescending { it.skill.priority }
        )
    }

    /**
     * 计算匹配分数
     */
    private fun calculateMatchScore(input: String, skill: Skill): Float {
        var score = 0f

        // 1. 关键词精确匹配（权重最高）
        for (keyword in skill.triggerKeywords) {
            val normalizedKeyword = keyword.lowercase()
            if (input.contains(normalizedKeyword)) {
                // 完整词匹配得分更高
                score += if (input.split(Regex("[\\s,，。.!?！？]+")).any {
                    it.trim() == normalizedKeyword
                }) {
                    2.0f
                } else {
                    1.0f
                }
            }
        }

        // 2. 描述中的关键词匹配（权重较低）
        val descriptionWords = skill.description.lowercase()
            .split(Regex("[\\s,，。.!?！？]+"))
            .filter { it.length > 1 }
            .distinct()

        for (word in descriptionWords) {
            if (input.contains(word)) {
                score += 0.2f
            }
        }

        // 3. 技能名称匹配
        if (input.contains(skill.name.lowercase())) {
            score += 1.5f
        }
        if (input.contains(skill.displayName.lowercase())) {
            score += 1.5f
        }

        return score
    }

    /**
     * 获取最佳匹配的技能
     *
     * @param userInput 用户输入
     * @param availableSkills 可用的技能列表
     * @param minScore 最低匹配分数阈值
     * @return 最佳匹配的技能，如果没有匹配则返回 null
     */
    fun getBestMatch(
        userInput: String,
        availableSkills: List<Skill>,
        minScore: Float = 0.5f
    ): Skill? {
        val matches = matchSkills(userInput, availableSkills)
        return matches.firstOrNull { it.score >= minScore }?.skill
    }

    /**
     * 获取所有匹配分数超过阈值的技能
     *
     * @param userInput 用户输入
     * @param availableSkills 可用的技能列表
     * @param minScore 最低匹配分数阈值
     * @param maxCount 最多返回的技能数量
     * @return 匹配的技能列表
     */
    fun getMatchedSkills(
        userInput: String,
        availableSkills: List<Skill>,
        minScore: Float = 0.5f,
        maxCount: Int = 3
    ): List<Skill> {
        return matchSkills(userInput, availableSkills)
            .filter { it.score >= minScore }
            .take(maxCount)
            .map { it.skill }
    }
}
