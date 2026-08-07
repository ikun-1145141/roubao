package com.roubao.autopilot.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 限流器
 *
 * 两层限流：
 * 1. **全局 QPS**：所有限制接口合计的每秒最大请求数（令牌桶）。
 * 2. **截图频率**：截图接口单独的最小间隔（毫秒），避免高频截屏冲击设备。
 *
 * 超限返回 false，由调用方决定如何响应（通常 429）。
 *
 * 线程安全：基于 AtomicLong 的时间戳比较，无锁实现。
 */
object RateLimiter {

    /** 全局每秒最大请求数（0 表示不限制） */
    @Volatile
    var globalQps: Int = 30

    /** 截图最小间隔（毫秒，0 表示不限制） */
    @Volatile
    var screenshotMinIntervalMs: Long = 500L

    /** 全局令牌桶：上次补充时间戳 */
    private val lastRefillTs = AtomicLong(System.currentTimeMillis())

    /** 全局令牌桶：当前可用令牌数 */
    private val availableTokens = AtomicLong(globalQps.toLong())

    /** 截图上次调用时间戳 */
    private val lastScreenshotTs = AtomicLong(0L)

    /** 各路径被限流次数（可观测性） */
    private val rejectedCounts = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 检查全局 QPS 是否允许通过
     *
     * 令牌桶算法：按时间流逝补充令牌，每次请求消耗 1 个。
     *
     * @return true 允许，false 被限流
     */
    fun checkGlobal(): Boolean {
        if (globalQps <= 0) return true  // 不限制
        val now = System.currentTimeMillis()
        // 补充令牌
        val last = lastRefillTs.get()
        val elapsed = now - last
        if (elapsed > 0) {
            // 每毫秒补充 globalQps/1000 个令牌
            val refill = (elapsed * globalQps / 1000).toLong()
            if (refill > 0 && lastRefillTs.compareAndSet(last, now)) {
                // 补充但不超过桶容量
                val current = availableTokens.get()
                val newTokens = minOf(current + refill, globalQps.toLong())
                availableTokens.set(newTokens)
            }
        }
        // 尝试消耗 1 个令牌
        return availableTokens.getAndUpdate { if (it > 0) it - 1 else 0 } > 0
    }

    /**
     * 检查截图频率是否允许通过
     *
     * @return true 允许，false 被限流
     */
    fun checkScreenshot(): Boolean {
        if (screenshotMinIntervalMs <= 0) return true
        val now = System.currentTimeMillis()
        val last = lastScreenshotTs.get()
        if (now - last < screenshotMinIntervalMs) {
            return false
        }
        return lastScreenshotTs.compareAndSet(last, now)
    }

    /**
     * 记录一次被限流（用于可观测性统计）
     *
     * @param uri 被限流的路径
     */
    fun recordRejection(uri: String) {
        rejectedCounts.computeIfAbsent(uri) { AtomicLong(0L) }.incrementAndGet()
    }

    /** 各路径被限流次数 */
    fun rejectedCounts(): Map<String, Long> =
        rejectedCounts.mapValues { it.value.get() }.toMap()

    /** 总被限流次数 */
    fun totalRejected(): Long =
        rejectedCounts.values.sumOf { it.get() }

    /** 导出为可观测性 Map */
    fun toMap(): Map<String, Any> = mapOf(
        "global_qps" to globalQps,
        "screenshot_min_interval_ms" to screenshotMinIntervalMs,
        "total_rejected" to totalRejected(),
        "rejected_counts" to rejectedCounts()
    )
}
