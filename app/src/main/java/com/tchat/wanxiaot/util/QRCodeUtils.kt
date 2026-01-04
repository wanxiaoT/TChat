package com.tchat.wanxiaot.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 二维码工具类
 * 使用ZXing库生成和解析二维码
 */
object QRCodeUtils {
    /**
     * 生成二维码
     * @param content 二维码内容
     * @param size 二维码尺寸（宽度和高度相同）
     * @param foregroundColor 前景色（默认黑色）
     * @param backgroundColor 背景色（默认白色）
     * @return 二维码Bitmap
     */
    fun generateQRCode(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从Bitmap中解析二维码
     * @param bitmap 包含二维码的Bitmap
     * @return 二维码内容，解析失败返回null
     */
    fun decodeQRCode(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = hashMapOf<DecodeHintType, Any>(
                DecodeHintType.CHARACTER_SET to "UTF-8",
                DecodeHintType.TRY_HARDER to true
            )

            val reader = QRCodeReader()
            val result = reader.decode(binaryBitmap, hints)
            result.text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成加密的二维码
     * @param content 原始内容
     * @param password 加密密码
     * @param size 二维码尺寸
     * @return 加密后的二维码Bitmap
     */
    fun generateEncryptedQRCode(
        content: String,
        password: String,
        size: Int = 512
    ): Bitmap? {
        val encrypted = EncryptionUtils.encrypt(content, password)
        return generateQRCode(encrypted, size)
    }

    /**
     * 解析加密的二维码
     * @param bitmap 包含加密二维码的Bitmap
     * @param password 解密密码
     * @return 解密后的内容，解析失败返回null
     */
    fun decodeEncryptedQRCode(bitmap: Bitmap, password: String): String? {
        val encrypted = decodeQRCode(bitmap) ?: return null
        return try {
            EncryptionUtils.decrypt(encrypted, password)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将ExportData转换为二维码
     * @param exportData 导出数据
     * @param password 可选的加密密码
     * @param size 二维码尺寸
     * @return 二维码Bitmap
     */
    fun exportDataToQRCode(
        exportData: ExportData,
        password: String? = null,
        size: Int = 512
    ): Bitmap? {
        val content = exportData.toJson()
        return if (password != null) {
            generateEncryptedQRCode(content, password, size)
        } else {
            generateQRCode(content, size)
        }
    }

    /**
     * 从二维码中解析ExportData
     * @param bitmap 包含二维码的Bitmap
     * @param password 可选的解密密码
     * @return 解析后的ExportData，失败返回null
     */
    fun qrCodeToExportData(bitmap: Bitmap, password: String? = null): ExportData? {
        val content = if (password != null) {
            decodeEncryptedQRCode(bitmap, password)
        } else {
            decodeQRCode(bitmap)
        } ?: return null

        return try {
            ExportData.fromJson(content)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
