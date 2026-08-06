package com.roubao.autopilot.server.dto

import org.json.JSONObject

/**
 * API 错误信息
 */
data class ApiError(
    val code: String,
    val message: String,
    val details: Any? = null
)

/**
 * 统一响应封装
 *
 * 成功：{ "success": true, "data": ..., "request_id": "...", "timestamp": ... }
 * 失败：{ "success": false, "error": { "code", "message", "details" }, "request_id": "...", "timestamp": ... }
 */
sealed class ApiResponse {
    data class Success(
        val data: Any?,
        val requestId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ApiResponse()

    data class Error(
        val error: ApiError,
        val requestId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ApiResponse()
}

/**
 * 错误码常量
 */
object ErrorCode {
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val NOT_FOUND = "NOT_FOUND"
    const val METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val TOOL_NOT_FOUND = "TOOL_NOT_FOUND"
    const val TOOL_EXECUTION_FAILED = "TOOL_EXECUTION_FAILED"
    const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"
}

/**
 * 把 ApiResponse 序列化为 JSON 字符串
 */
fun ApiResponse.toJsonString(): String {
    return when (this) {
        is ApiResponse.Success -> {
            val dataJson = when (data) {
                is String -> JSONObject().put("value", data).toString().let {
                    // 若 data 本身就是合法 JSON 对象/数组字符串，直接嵌入
                    try {
                        JSONObject(data).toString()
                    } catch (_: Exception) {
                        try {
                            org.json.JSONArray(data).toString()
                        } catch (_: Exception) {
                            JSONObject().put("value", data).toString()
                        }
                    }
                }
                is Map<*, *> -> JSONObject(data).toString()
                is List<*> -> org.json.JSONArray(data).toString()
                null -> "null"
                else -> JSONObject().put("value", data).toString()
            }
            """{"success":true,"data":$dataJson,"request_id":"$requestId","timestamp":$timestamp}"""
        }
        is ApiResponse.Error -> {
            val detailsJson = when (val d = error.details) {
                null -> "null"
                is String -> "\"${d.replace("\"", "\\\"")}\""
                is Map<*, *> -> JSONObject(d).toString()
                is List<*> -> org.json.JSONArray(d).toString()
                else -> JSONObject().put("value", d).toString()
            }
            """{"success":false,"error":{"code":"${error.code}","message":"${error.message.replace("\"", "\\\"")}\"","details":$detailsJson},"request_id":"$requestId","timestamp":$timestamp}"""
        }
    }
}
