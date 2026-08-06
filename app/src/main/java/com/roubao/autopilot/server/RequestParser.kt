package com.roubao.autopilot.server

import com.roubao.autopilot.server.dto.ApiError
import com.roubao.autopilot.server.dto.ApiResponse
import com.roubao.autopilot.server.dto.ErrorCode
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

/**
 * 请求解析工具
 *
 * NanoHTTPD 解析 POST body 需要先 parseBody，再把 JSON 字段取出
 */
object RequestParser {

    /**
     * 解析 POST/PUT 的 JSON body 为 Map
     */
    fun parseJsonBody(session: NanoHTTPD.IHTTPSession): Map<String, Any?> {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val rawJson = files["postData"] ?: session.parameters["postData"]?.firstOrNull() ?: ""
        if (rawJson.isBlank()) return emptyMap()

        return try {
            val json = JSONObject(rawJson)
            json.toDeepMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 从 session query 参数中取值
     */
    fun queryParam(session: NanoHTTPD.IHTTPSession, key: String): String? {
        return session.parameters[key]?.firstOrNull()
    }

    /**
     * 把 JSONObject 转 Map（递归）
     */
    fun JSONObject.toDeepMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in this.keys()) {
            map[key] = when (val v = this.get(key)) {
                is JSONObject -> v.toDeepMap()
                is JSONArray -> v.toDeepList()
                JSONObject.NULL -> null
                else -> v
            }
        }
        return map
    }

    /**
     * 把 JSONArray 转 List（递归）
     */
    fun JSONArray.toDeepList(): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until this.length()) {
            val v = this.get(i)
            list.add(
                when (v) {
                    is JSONObject -> v.toDeepMap()
                    is JSONArray -> v.toDeepList()
                    JSONObject.NULL -> null
                    else -> v
                }
            )
        }
        return list
    }

    /**
     * 把 Tool 执行结果映射为 ApiResponse
     */
    fun mapToolResult(result: com.roubao.autopilot.tools.ToolResult, requestId: String): ApiResponse {
        return when (result) {
            is com.roubao.autopilot.tools.ToolResult.Success ->
                ApiResponse.Success(result.data, requestId)
            is com.roubao.autopilot.tools.ToolResult.Error ->
                ApiResponse.Error(
                    ApiError(ErrorCode.TOOL_EXECUTION_FAILED, result.error),
                    requestId
                )
        }
    }
}
