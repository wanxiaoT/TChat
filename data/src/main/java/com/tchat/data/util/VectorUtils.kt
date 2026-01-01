package com.tchat.data.util

import kotlin.math.sqrt

/**
 * 向量计算工具类
 */
object VectorUtils {
    /**
     * 计算两个向量的余弦相似度
     * @return 相似度分数 (-1 到 1 之间)
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }

    /**
     * 计算两个向量的点积
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }

        var result = 0f
        for (i in a.indices) {
            result += a[i] * b[i]
        }
        return result
    }

    /**
     * 计算两个向量的欧几里得距离
     */
    fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same dimension" }

        var sumSquared = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sumSquared += diff * diff
        }
        return sqrt(sumSquared)
    }

    /**
     * 归一化向量到单位长度
     */
    fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) {
            norm += v * v
        }
        norm = sqrt(norm)

        return if (norm == 0f) {
            vector.copyOf()
        } else {
            FloatArray(vector.size) { i -> vector[i] / norm }
        }
    }
}
