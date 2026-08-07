package com.roubao.autopilot.server.handlers

import com.roubao.autopilot.server.RequestParser
import com.roubao.autopilot.tools.ToolManager
import fi.iki.elonen.NanoHTTPD

/**
 * 设备控制域 Handler
 *
 * /api/device/screenshot
 * /api/device/tap
 * /api/device/double_tap
 * /api/device/long_press
 * /api/device/swipe
 * /api/device/input
 * /api/device/key
 * /api/device/clipboard
 */
class DeviceHandler {

    /**
     * 通用 Tool 执行入口
     *
     * 把请求 body 透传给 ToolManager.execute(toolName, params)
     */
    suspend fun executeTool(
        toolName: String,
        session: NanoHTTPD.IHTTPSession
    ): Any {
        val params = RequestParser.parseJsonBody(session)
        val result = ToolManager.getInstance().execute(toolName, params)
        return result
    }

    /**
     * 截图（GET，参数从 query 取）
     *
     * 支持 query 参数：format、quality、scale（与 ScreenshotTool 的参数对齐）
     */
    suspend fun screenshot(session: NanoHTTPD.IHTTPSession): Any {
        val params = mutableMapOf<String, Any?>()
        RequestParser.queryParam(session, "format")?.let { params["format"] = it }
        RequestParser.queryParam(session, "quality")?.toIntOrNull()?.let { params["quality"] = it }
        RequestParser.queryParam(session, "scale")?.toFloatOrNull()?.let { params["scale"] = it }
        val result = ToolManager.getInstance().execute("screenshot", params)
        return result
    }

    /**
     * 获取剪贴板（GET，无 body）
     */
    suspend fun getClipboard(@Suppress("UNUSED_PARAMETER") session: NanoHTTPD.IHTTPSession): Any {
        // clipboard tool 需要 action=read
        val result = ToolManager.getInstance().execute("clipboard", mapOf("action" to "read"))
        return result
    }
}
