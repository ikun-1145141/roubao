package com.roubao.autopilot.server.handlers

import com.roubao.autopilot.server.RequestParser
import com.roubao.autopilot.tools.ToolManager
import fi.iki.elonen.NanoHTTPD

/**
 * 应用域 Handler
 *
 * /api/apps/list
 * /api/apps/search
 * /api/apps/open
 * /api/apps/deep_link
 * /api/apps/current
 */
class AppHandler {

    /**
     * 搜索应用（GET，参数从 query 取）
     */
    suspend fun searchApps(session: NanoHTTPD.IHTTPSession): Any {
        val query = RequestParser.queryParam(session, "q") ?: ""
        val topK = RequestParser.queryParam(session, "top_k")?.toIntOrNull() ?: 10
        val includeSystem =
            RequestParser.queryParam(session, "include_system")?.toBooleanStrictOrNull() ?: false

        val params = mapOf(
            "query" to query,
            "top_k" to topK,
            "include_system" to includeSystem
        )
        return ToolManager.getInstance().execute("search_apps", params)
    }

    /**
     * 列出应用
     */
    suspend fun listApps(session: NanoHTTPD.IHTTPSession): Any {
        val topK = RequestParser.queryParam(session, "top_k")?.toIntOrNull() ?: 50
        val includeSystem =
            RequestParser.queryParam(session, "include_system")?.toBooleanStrictOrNull() ?: false

        // 复用 search_apps，传空 query 触发列出全部
        val params = mapOf(
            "query" to "",
            "top_k" to topK,
            "include_system" to includeSystem
        )
        return ToolManager.getInstance().execute("search_apps", params)
    }

    /**
     * 打开应用
     */
    suspend fun openApp(session: NanoHTTPD.IHTTPSession): Any {
        val params = RequestParser.parseJsonBody(session)
        return ToolManager.getInstance().execute("open_app", params)
    }

    /**
     * 打开 Deep Link
     */
    suspend fun deepLink(session: NanoHTTPD.IHTTPSession): Any {
        val params = RequestParser.parseJsonBody(session)
        return ToolManager.getInstance().execute("deep_link", params)
    }

    /**
     * 当前应用
     */
    suspend fun currentApp(@Suppress("UNUSED_PARAMETER") session: NanoHTTPD.IHTTPSession): Any {
        return ToolManager.getInstance().execute("current_app", emptyMap())
    }
}
