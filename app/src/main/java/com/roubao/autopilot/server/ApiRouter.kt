package com.roubao.autopilot.server

import com.roubao.autopilot.server.dto.ApiError
import com.roubao.autopilot.server.dto.ApiResponse
import com.roubao.autopilot.server.dto.ErrorCode
import com.roubao.autopilot.server.dto.toJsonString
import com.roubao.autopilot.server.handlers.AppHandler
import com.roubao.autopilot.server.handlers.DeviceHandler
import com.roubao.autopilot.server.handlers.ShellHandler
import com.roubao.autopilot.server.handlers.SystemHandler
import com.roubao.autopilot.tools.ToolManager
import com.roubao.autopilot.tools.ToolResult
import fi.iki.elonen.NanoHTTPD
import java.util.UUID

/**
 * API 路由分发器
 *
 * 负责路由匹配、鉴权、统一响应封装，并把业务分发给各 Handler
 */
class ApiRouter(
    private val authToken: String?
) {
    private val systemHandler = SystemHandler()
    private val deviceHandler = DeviceHandler()
    private val appHandler = AppHandler()
    private val shellHandler = ShellHandler()

    /**
     * 处理请求（suspend，由 HttpServerService.runBlocking 桥接）
     *
     * @return NanoHTTPD 的 Response
     */
    suspend fun route(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val requestId = UUID.randomUUID().toString()
        val uri = session.uri.trimEnd('/')
        val method = session.method

        try {
            // 鉴权（仅当配置了 token 时校验）
            if (!authToken.isNullOrEmpty()) {
                val headerToken = session.headers["authorization"]
                    ?.removePrefix("Bearer ")
                    ?.trim()
                val queryToken = session.parameters["token"]?.firstOrNull()
                val provided = headerToken ?: queryToken
                if (provided != authToken) {
                    return jsonError(
                        ApiResponse.Error(
                            ApiError(ErrorCode.UNAUTHORIZED, "鉴权失败：Token 无效或缺失"),
                            requestId
                        ),
                        NanoHTTPD.Response.Status.UNAUTHORIZED
                    )
                }
            }

            // 路由
            when {
                // 健康检查（无需鉴权放在鉴权之后即可，token 为空时也开放）
                uri == "/api/system/ping" && method == NanoHTTPD.Method.GET ->
                    return jsonOk(systemHandler.ping(), requestId)

                uri == "/api/system/status" && method == NanoHTTPD.Method.GET ->
                    return jsonOk(systemHandler.status(), requestId)

                // 设备控制域
                uri == "/api/device/screenshot" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(deviceHandler.screenshot(session), requestId)

                uri == "/api/device/tap" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(deviceHandler.executeTool("tap", session), requestId)

                uri == "/api/device/double_tap" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(deviceHandler.executeTool("double_tap", session), requestId)

                uri == "/api/device/long_press" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(deviceHandler.executeTool("long_press", session), requestId)

                uri == "/api/device/swipe" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(deviceHandler.executeTool("swipe", session), requestId)

                uri == "/api/device/input" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(deviceHandler.executeTool("input_text", session), requestId)

                uri == "/api/device/key" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(deviceHandler.executeTool("key", session), requestId)

                uri == "/api/device/clipboard" && method == NanoHTTPD.Method.GET ->
                    return jsonFromTool(deviceHandler.getClipboard(session), requestId)

                // 应用域
                uri == "/api/apps/list" && method == NanoHTTPD.Method.GET ->
                    return jsonFromTool(appHandler.listApps(session), requestId)

                uri == "/api/apps/search" && method == NanoHTTPD.Method.GET ->
                    return jsonFromTool(appHandler.searchApps(session), requestId)

                uri == "/api/apps/open" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(appHandler.openApp(session), requestId)

                uri == "/api/apps/deep_link" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(appHandler.deepLink(session), requestId)

                uri == "/api/apps/current" && method == NanoHTTPD.Method.GET ->
                    return jsonFromTool(appHandler.currentApp(session), requestId)

                // 高级域
                uri == "/api/advanced/shell" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(shellHandler.executeShell(session), requestId)

                uri == "/api/advanced/http" && method == NanoHTTPD.Method.POST ->
                    return jsonFromTool(shellHandler.executeHttp(session), requestId)

                // 工具描述（调试用）
                uri == "/api/system/tools" && method == NanoHTTPD.Method.GET ->
                    return jsonOk(mapOf("tools" to ToolManager.getInstance().getToolDescriptions()), requestId)

                else -> {
                    // 未匹配
                    if (uri.startsWith("/api/")) {
                        return jsonError(
                            ApiResponse.Error(
                                ApiError(ErrorCode.NOT_FOUND, "未找到路由: $method $uri"),
                                requestId
                            ),
                            NanoHTTPD.Response.Status.NOT_FOUND
                        )
                    }
                    // 非 /api/ 路径返回根信息
                    return jsonOk(
                        mapOf(
                            "name" to "Roubao Autopilot API",
                            "version" to "v1",
                            "endpoints" to listOf("/api/system/ping", "/api/system/status")
                        ),
                        requestId
                    )
                }
            }
        } catch (e: IllegalStateException) {
            // ToolManager 未初始化
            return jsonError(
                ApiResponse.Error(
                    ApiError(ErrorCode.SERVICE_UNAVAILABLE, "服务未就绪：${e.message}"),
                    requestId
                ),
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE
            )
        } catch (e: Exception) {
            return jsonError(
                ApiResponse.Error(
                    ApiError(ErrorCode.INTERNAL_ERROR, e.message ?: "内部错误"),
                    requestId
                ),
                NanoHTTPD.Response.Status.INTERNAL_ERROR
            )
        }
    }

    private fun jsonOk(data: Any?, requestId: String): NanoHTTPD.Response {
        val body = ApiResponse.Success(data, requestId).toJsonString()
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            body
        )
    }

    /**
     * 把 ToolResult 映射为统一响应
     */
    private fun jsonFromTool(result: Any, requestId: String): NanoHTTPD.Response {
        val (apiResponse, status) = when (result) {
            is ToolResult.Success ->
                ApiResponse.Success(result.data, requestId) to NanoHTTPD.Response.Status.OK
            is ToolResult.Error ->
                ApiResponse.Error(
                    ApiError(ErrorCode.TOOL_EXECUTION_FAILED, result.error),
                    requestId
                ) to NanoHTTPD.Response.Status.INTERNAL_ERROR
            else ->
                ApiResponse.Success(result, requestId) to NanoHTTPD.Response.Status.OK
        }
        val body = apiResponse.toJsonString()
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body)
    }

    private fun jsonError(
        error: ApiResponse.Error,
        status: NanoHTTPD.Response.Status
    ): NanoHTTPD.Response {
        val body = error.toJsonString()
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body)
    }
}
