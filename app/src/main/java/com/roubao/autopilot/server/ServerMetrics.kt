package com.roubao.autopilot.server

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

/**
 * Server 可观测性指标
 *
 * 线程安全地统计：总请求数、错误数、平均延迟、最近客户端 IP。
 * 由 ApiRouter 在每个请求结束时记录，由 SystemHandler.status() 暴露。
 */
object ServerMetrics {

    private val totalRequests = AtomicLong(0L)
    private val errorRequests = AtomicLong(0L)
    private val totalLatencyMs = AtomicLong(0L)

    /** 最近连接的客户端 IP（最多保留 5 个，去重） */
    private val recentClientIps = Collections.synchronizedList(mutableListOf<String>())

    /** 各路径调用次数（粗粒度，按 uri 统计） */
    private val pathCounts = ConcurrentHashMap<String, AtomicLong>()

    /** 服务启动时间 */
    val startedAt: Long = System.currentTimeMillis()

    /**
     * 记录一次请求
     *
     * @param uri 请求路径
     * @param latencyMs 耗时（毫秒）
     * @param success 是否成功（HTTP 2xx 视为成功）
     * @param clientIp 客户端 IP（可空）
     */
    fun record(uri: String, latencyMs: Long, success: Boolean, clientIp: String?) {
        totalRequests.incrementAndGet()
        totalLatencyMs.addAndGet(latencyMs)
        if (!success) {
            errorRequests.incrementAndGet()
        }
        pathCounts.computeIfAbsent(uri) { AtomicLong(0L) }.incrementAndGet()

        if (!clientIp.isNullOrBlank()) {
            synchronized(recentClientIps) {
                recentClientIps.remove(clientIp)
                recentClientIps.add(0, clientIp)
                while (recentClientIps.size > 5) {
                    recentClientIps.removeAt(recentClientIps.size - 1)
                }
            }
        }
    }

    /** 总请求数 */
    fun totalRequests(): Long = totalRequests.get()

    /** 错误请求数 */
    fun errorRequests(): Long = errorRequests.get()

    /** 平均延迟（毫秒），无请求时返回 0 */
    fun avgLatencyMs(): Long {
        val total = totalRequests.get()
        return if (total == 0L) 0L else totalLatencyMs.get() / total
    }

    /** 最近客户端 IP 列表（最新在前） */
    fun recentClientIps(): List<String> = synchronized(recentClientIps) { recentClientIps.toList() }

    /** 各路径调用次数 */
    fun pathCounts(): Map<String, Long> =
        pathCounts.mapValues { it.value.get() }.toMap()

    /** 运行时长（毫秒） */
    fun uptimeMs(): Long = System.currentTimeMillis() - startedAt

    /**
     * 导出为 status 接口可用的 Map
     */
    fun toMap(): Map<String, Any> = mapOf(
        "total_requests" to totalRequests(),
        "error_requests" to errorRequests(),
        "avg_latency_ms" to avgLatencyMs(),
        "uptime_ms" to uptimeMs(),
        "recent_client_ips" to recentClientIps(),
        "path_counts" to pathCounts(),
        "device_mutex" to DeviceMutex.toMap(),
        "rate_limiter" to RateLimiter.toMap()
    )
}
