package com.roubao.autopilot.tools

import com.roubao.autopilot.controller.DeviceController

/**
 * 文本输入工具
 *
 * 通过 Shizuku 执行输入文本（支持中文，自动走剪贴板方式）
 */
class InputTool(
    private val deviceController: DeviceController
) : Tool {

    override val name = "input_text"
    override val displayName = "输入文本"
    override val description = "在当前焦点输入框输入文本（支持中文）"

    override val params = listOf(
        ToolParam("text", "string", "要输入的文本内容", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val text = params["text"] as? String
            ?: return ToolResult.Error("缺少 text 参数")

        if (text.isEmpty()) {
            return ToolResult.Error("text 不能为空")
        }

        return try {
            deviceController.type(text)
            ToolResult.Success(
                data = mapOf("text" to text),
                message = "已输入文本: $text"
            )
        } catch (e: Exception) {
            ToolResult.Error("输入文本失败: ${e.message}")
        }
    }
}
