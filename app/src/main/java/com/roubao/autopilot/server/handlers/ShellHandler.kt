package com.roubao.autopilot.server.handlers

import com.roubao.autopilot.server.RequestParser
import com.roubao.autopilot.tools.ToolManager
import fi.iki.elonen.NanoHTTPD

/**
 * 高级域 Handler
 *
 * /api/advanced/shell
 * /api/advanced/http
 *
 * 注意：这两个能力较危险，建议仅在 root 模式下开放，或由鉴权 Token 控制
 */
class ShellHandler {

    /**
     * 执行 shell 命令
     *
     * body: { "command": "...", "timeout": 5000 }
     */
    suspend fun executeShell(session: NanoHTTPD.IHTTPSession): Any {
        val params = RequestParser.parseJsonBody(session)
        return ToolManager.getInstance().execute("shell", params)
    }

    /**
     * 执行 HTTP 请求
     *
     * body: { "url": "...", "method": "GET", "headers": {...}, "body": "..." }
     */
    suspend fun executeHttp(session: NanoHTTPD.IHTTPSession): Any {
        val params = RequestParser.parseJsonBody(session)
        return ToolManager.getInstance().execute("http", params)
    }
}
