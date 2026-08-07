package com.roubao.autopilot.server.handlers

import com.roubao.autopilot.server.RequestParser
import com.roubao.autopilot.tools.ToolManager
import com.roubao.autopilot.tools.ToolResult
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务域 Handler
 *
 * 长任务管理：提交一个 Tool 执行作为后台任务，通过 SSE 推送进度/结果。
 *
 * /api/tasks/submit   POST   提交长任务，立即返回 task_id
 * /api/tasks/:id      GET    查询任务状态（JSON）
 * /api/tasks/:id/stream GET  SSE 流，实时推送任务进度与结果
 * /api/tasks          GET    列出所有任务
 * /api/tasks/:id      DELETE 取消任务
 *
 * 设计说明：
 * - 当前 Tool 层没有细粒度进度回调，SSE 流在任务完成时推送最终结果，
 *   在任务运行期间每秒推送一次心跳（保持连接 + 表明任务仍在运行）。
 * - 后续若 Tool 层支持进度回调，可在此转发。
 */
class TaskHandler {

    /** 任务状态 */
    enum class TaskState { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    /** 任务记录 */
    data class Task(
        val id: String,
        val toolName: String,
        val params: Map<String, Any?>,
        val createdAt: Long = System.currentTimeMillis(),
        @Volatile var state: TaskState = TaskState.PENDING,
        @Volatile var result: Any? = null,
        @Volatile var error: String? = null,
        @Volatile var finishedAt: Long? = null
    )

    /** 任务存储（进程内，应用生命周期内有效） */
    private val tasks = ConcurrentHashMap<String, Task>()

    /** 运行中的协程任务，用于取消 */
    private val jobs = ConcurrentHashMap<String, Job>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 提交任务
     *
     * body: { "tool": "screenshot", "params": { ... } }
     * 返回: { "task_id": "...", "state": "RUNNING" }
     */
    suspend fun submit(session: NanoHTTPD.IHTTPSession): Any {
        val body = RequestParser.parseJsonBody(session)
        val toolName = body["tool"] as? String
            ?: return ToolResult.Error("缺少 tool 参数")

        @Suppress("UNCHECKED_CAST")
        val params = (body["params"] as? Map<String, Any?>) ?: emptyMap()

        val taskId = UUID.randomUUID().toString()
        val task = Task(id = taskId, toolName = toolName, params = params)
        tasks[taskId] = task

        // 启动后台执行
        task.state = TaskState.RUNNING
        val job = scope.launch {
            try {
                val result = ToolManager.getInstance().execute(toolName, params)
                when (result) {
                    is ToolResult.Success -> {
                        task.result = result.data
                        task.state = TaskState.SUCCEEDED
                    }
                    is ToolResult.Error -> {
                        task.error = result.error
                        task.state = TaskState.FAILED
                    }
                }
            } catch (e: Exception) {
                task.error = e.message ?: "任务执行异常"
                task.state = TaskState.FAILED
            } finally {
                task.finishedAt = System.currentTimeMillis()
                jobs.remove(taskId)
            }
        }
        jobs[taskId] = job

        return mapOf(
            "task_id" to taskId,
            "state" to task.state.name,
            "tool" to toolName
        )
    }

    /**
     * 查询任务状态
     */
    fun getStatus(taskId: String): Any {
        val task = tasks[taskId]
            ?: return ToolResult.Error("任务不存在: $taskId")

        return mapOf(
            "task_id" to task.id,
            "tool" to task.toolName,
            "state" to task.state.name,
            "created_at" to task.createdAt,
            "finished_at" to task.finishedAt,
            "result" to (task.result ?: ""),
            "error" to (task.error ?: "")
        )
    }

    /**
     * 列出所有任务
     */
    fun listTasks(): Any {
        return tasks.values.map { task ->
            mapOf(
                "task_id" to task.id,
                "tool" to task.toolName,
                "state" to task.state.name,
                "created_at" to task.createdAt,
                "finished_at" to task.finishedAt
            )
        }
    }

    /**
     * 取消任务
     */
    fun cancel(taskId: String): Any {
        val task = tasks[taskId]
            ?: return ToolResult.Error("任务不存在: $taskId")
        jobs[taskId]?.cancel()
        task.state = TaskState.CANCELLED
        task.finishedAt = System.currentTimeMillis()
        jobs.remove(taskId)
        return mapOf(
            "task_id" to taskId,
            "state" to task.state.name
        )
    }

    /**
     * SSE 流：推送任务进度与最终结果
     *
     * 返回 NanoHTTPD.Response（text/event-stream），由 ApiRouter 直接返回。
     */
    fun stream(taskId: String): NanoHTTPD.Response {
        val task = tasks[taskId]
            ?: return errorResponse("任务不存在: $taskId")

        // NanoHTTPD 的 ChunkedResponse 支持 SSE
        val pipedInputStream = java.io.PipedInputStream(16 * 1024)
        val pipedOutputStream = java.io.PipedOutputStream(pipedInputStream)

        // 后台线程持续推送
        Thread {
            try {
                fun sendEvent(event: String, data: String) {
                    pipedOutputStream.write("event: $event\ndata: $data\n\n".toByteArray())
                    pipedOutputStream.flush()
                }

                // 初始事件
                sendEvent("status", """{"task_id":"${task.id}","state":"${task.state.name}"}""")

                // 心跳循环，直到任务结束
                while (task.state == TaskState.RUNNING || task.state == TaskState.PENDING) {
                    Thread.sleep(1000)
                    sendEvent("heartbeat", """{"ts":${System.currentTimeMillis()}}""")
                }

                // 任务结束，推送最终结果
                val finalData = when (task.state) {
                    TaskState.SUCCEEDED -> """{"task_id":"${task.id}","state":"SUCCEEDED","result":${encodeJson(task.result)}}"""
                    TaskState.FAILED -> """{"task_id":"${task.id}","state":"FAILED","error":"${task.error ?: ""}"}"""
                    TaskState.CANCELLED -> """{"task_id":"${task.id}","state":"CANCELLED"}"""
                    else -> """{"task_id":"${task.id}","state":"${task.state.name}"}"""
                }
                sendEvent("complete", finalData)
            } catch (e: Exception) {
                // 连接断开等，忽略
            } finally {
                try { pipedOutputStream.close() } catch (_: Exception) {}
            }
        }.also { it.isDaemon = true }.start()

        return NanoHTTPD.newChunkedResponse(
            NanoHTTPD.Response.Status.OK,
            "text/event-stream; charset=utf-8",
            pipedInputStream
        )
    }

    /**
     * 简易 JSON 值编码（避免引入完整 JSON 库的依赖）
     */
    private fun encodeJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            is Number, is Boolean -> value.toString()
            else -> "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
    }

    private fun errorResponse(message: String): NanoHTTPD.Response {
        val body = """{"success":false,"error":{"code":"NOT_FOUND","message":"$message"}}"""
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "application/json; charset=utf-8",
            body
        )
    }
}
