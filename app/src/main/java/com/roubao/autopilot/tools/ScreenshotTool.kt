package com.roubao.autopilot.tools

import android.graphics.Bitmap
import android.util.Base64
import com.roubao.autopilot.controller.DeviceController
import java.io.ByteArrayOutputStream

/**
 * 截图工具
 *
 * 通过 Shizuku 截取屏幕，返回 base64 编码的图片与屏幕尺寸
 *
 * 支持参数：
 * - format: png 或 jpeg，默认 jpeg
 * - quality: JPEG 质量 1-100，默认 70
 * - scale: 缩放比例 0.25-1.0，默认 1.0
 */
class ScreenshotTool(
    private val deviceController: DeviceController
) : Tool {

    override val name = "screenshot"
    override val displayName = "屏幕截图"
    override val description = "截取当前屏幕，返回 base64 图片与屏幕尺寸"

    override val params = listOf(
        ToolParam(
            name = "format",
            type = "string",
            description = "图片格式：png 或 jpeg，默认 jpeg",
            required = false,
            defaultValue = "jpeg"
        ),
        ToolParam(
            name = "quality",
            type = "int",
            description = "JPEG 质量 1-100，默认 70",
            required = false,
            defaultValue = 70
        ),
        ToolParam(
            name = "scale",
            type = "float",
            description = "缩放比例 0.25-1.0，默认 1.0",
            required = false,
            defaultValue = 1.0f
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val format = (params["format"] as? String)?.lowercase() ?: "jpeg"
        val quality = (params["quality"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 70
        val scale = (params["scale"] as? Number)?.toFloat()?.coerceIn(0.25f, 1.0f) ?: 1.0f

        return try {
            val screenshotResult = deviceController.screenshotWithFallback()
            val originalBitmap = screenshotResult.bitmap

            val (screenWidth, screenHeight) = deviceController.getScreenSize()

            // 缩放
            val scaledBitmap = if (scale < 1.0f) {
                val newW = (originalBitmap.width * scale).toInt().coerceAtLeast(1)
                val newH = (originalBitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)
            } else {
                originalBitmap
            }

            // 编码
            val compressFormat = if (format == "png") {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            val useQuality = if (format == "png") 100 else quality

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(compressFormat, useQuality, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // 回收缩放后的临时位图
            if (scale < 1.0f && scaledBitmap !== originalBitmap) {
                scaledBitmap.recycle()
            }

            ToolResult.Success(
                data = mapOf(
                    "image" to base64,
                    "format" to format,
                    "screen_width" to screenWidth,
                    "screen_height" to screenHeight,
                    "image_width" to (originalBitmap.width * scale).toInt(),
                    "image_height" to (originalBitmap.height * scale).toInt(),
                    "is_sensitive" to screenshotResult.isSensitive,
                    "is_fallback" to screenshotResult.isFallback
                ),
                message = "截图完成 ${imageBytes.size} bytes (${format})"
            )
        } catch (e: Exception) {
            ToolResult.Error("截图失败: ${e.message}")
        }
    }
}
