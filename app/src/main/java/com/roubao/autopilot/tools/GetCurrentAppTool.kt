package com.roubao.autopilot.tools

import com.roubao.autopilot.controller.DeviceController

/**
 * 获取当前前台应用工具
 *
 * 返回当前处于前台的应用包名
 */
class GetCurrentAppTool(
    private val deviceController: DeviceController
) : Tool {

    override val name = "current_app"
    override val displayName = "当前应用"
    override val description = "获取当前处于前台的应用包名"

    override val params = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return try {
            val packageName = deviceController.getCurrentApp()
            if (packageName.isNotEmpty()) {
                ToolResult.Success(
                    data = mapOf("package" to packageName),
                    message = "当前前台应用: $packageName"
                )
            } else {
                ToolResult.Error("无法获取当前前台应用")
            }
        } catch (e: Exception) {
            ToolResult.Error("获取当前应用失败: ${e.message}")
        }
    }
}
