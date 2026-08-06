package com.roubao.autopilot.tools

import com.roubao.autopilot.controller.DeviceController

/**
 * 滑动工具
 *
 * 通过 Shizuku 执行 input swipe 命令滑动屏幕
 */
class SwipeTool(
    private val deviceController: DeviceController
) : Tool {

    override val name = "swipe"
    override val displayName = "滑动"
    override val description = "从坐标 (x1,y1) 滑动到 (x2,y2)"

    override val params = listOf(
        ToolParam("x1", "int", "起点 x 坐标（像素）", required = true),
        ToolParam("y1", "int", "起点 y 坐标（像素）", required = true),
        ToolParam("x2", "int", "终点 x 坐标（像素）", required = true),
        ToolParam("y2", "int", "终点 y 坐标（像素）", required = true),
        ToolParam("duration", "int", "滑动时长（毫秒），默认 500", required = false, defaultValue = 500)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val x1 = (params["x1"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 x1 参数")
        val y1 = (params["y1"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 y1 参数")
        val x2 = (params["x2"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 x2 参数")
        val y2 = (params["y2"] as? Number)?.toInt()
            ?: return ToolResult.Error("缺少或无效的 y2 参数")
        val duration = (params["duration"] as? Number)?.toInt() ?: 500

        return try {
            deviceController.swipe(x1, y1, x2, y2, duration)
            ToolResult.Success(
                data = mapOf(
                    "x1" to x1, "y1" to y1,
                    "x2" to x2, "y2" to y2,
                    "duration" to duration
                ),
                message = "已滑动 ($x1,$y1) -> ($x2,$y2) ${duration}ms"
            )
        } catch (e: Exception) {
            ToolResult.Error("滑动失败: ${e.message}")
        }
    }
}
