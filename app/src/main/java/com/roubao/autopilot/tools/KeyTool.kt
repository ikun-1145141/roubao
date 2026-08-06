package com.roubao.autopilot.tools

import com.roubao.autopilot.controller.DeviceController

/**
 * 按键工具
 *
 * 发送系统按键（Home/Back/Recent/Enter/Delete/音量等）
 */
class KeyTool(
    private val deviceController: DeviceController
) : Tool {

    override val name = "key"
    override val displayName = "系统按键"
    override val description = "发送系统按键（home/back/recent/enter/delete/volume_up/volume_down/power/menu）"

    override val params = listOf(
        ToolParam(
            name = "key",
            type = "string",
            description = "按键名：home/back/recent/enter/delete/menu/volume_up/volume_down/mute/power",
            required = true
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val key = params["key"] as? String
            ?: return ToolResult.Error("缺少 key 参数")

        return try {
            deviceController.key(key)
            ToolResult.Success(
                data = mapOf("key" to key),
                message = "已发送按键: $key"
            )
        } catch (e: Exception) {
            ToolResult.Error("按键失败: ${e.message}")
        }
    }
}
