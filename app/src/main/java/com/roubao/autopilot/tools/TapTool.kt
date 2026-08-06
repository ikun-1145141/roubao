package com.roubao.autopilot.tools

import com.roubao.autopilot.controller.DeviceController

/**
 * 点击屏幕工具
 *
 * 通过 Shizuku 执行 input tap 命令点击指定坐标
 */
class TapTool(
    private val deviceController: DeviceController
) : Tool {

    override val name = "tap"
    override val displayName = "点击屏幕"
    override val description = "点击屏幕指定坐标位置"

    override val params = listOf(
        ToolParam(
            name = "x",
            type = "int",
            description = "点击的 x 坐标（像素）",
            required = true
        ),
        ToolParam(
            name = "y",
            type = "int",
            description = "点击的 y 坐标（像素）",
            required = true
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val x = (params["x"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 x 参数")
        val y = (params["y"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 y 参数")

        return try {
            deviceController.tap(x, y)
            ToolResult.Success(
                data = mapOf("x" to x, "y" to y),
                message = "已点击 ($x, $y)"
            )
        } catch (e: Exception) {
            ToolResult.Error("点击失败: ${e.message}")
        }
    }
}

/**
 * 长按工具
 */
class LongPressTool(
    private val deviceController: DeviceController
) : Tool {

    override val name = "long_press"
    override val displayName = "长按"
    override val description = "长按屏幕指定坐标"

    override val params = listOf(
        ToolParam("x", "int", "长按的 x 坐标（像素）", required = true),
        ToolParam("y", "int", "长按的 y 坐标（像素）", required = true),
        ToolParam("duration", "int", "长按时长（毫秒），默认 1000", required = false, defaultValue = 1000)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val x = (params["x"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 x 参数")
        val y = (params["y"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 y 参数")
        val duration = (params["duration"] as? Number)?.toInt() ?: 1000

        return try {
            deviceController.longPress(x, y, duration)
            ToolResult.Success(
                data = mapOf("x" to x, "y" to y, "duration" to duration),
                message = "已长按 ($x, $y) ${duration}ms"
            )
        } catch (e: Exception) {
            ToolResult.Error("长按失败: ${e.message}")
        }
    }
}

/**
 * 双击工具
 */
class DoubleTapTool(
    private val deviceController: DeviceController
) : Tool {

    override val name = "double_tap"
    override val displayName = "双击"
    override val description = "双击屏幕指定坐标"

    override val params = listOf(
        ToolParam("x", "int", "双击的 x 坐标（像素）", required = true),
        ToolParam("y", "int", "双击的 y 坐标（像素）", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val x = (params["x"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 x 参数")
        val y = (params["y"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 y 参数")

        return try {
            deviceController.doubleTap(x, y)
            ToolResult.Success(
                data = mapOf("x" to x, "y" to y),
                message = "已双击 ($x, $y)"
            )
        } catch (e: Exception) {
            ToolResult.Error("双击失败: ${e.message}")
        }
    }
}
