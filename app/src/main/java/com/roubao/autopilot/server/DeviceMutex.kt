package com.roubao.autopilot.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * 设备操作互斥锁
 *
 * 确保对设备的物理操作（点击/滑动/输入/按键/截图/Shell 等）串行执行，
 * 避免并发请求导致操作错乱或 Shizuku 竞态。
 *
 * - 只读且无副作用的接口（ping/status/list/search/current）无需加锁。
 * - 截图虽是"读"，但底层调用 Shizuku 截屏服务，与 tap 等操作共享同一设备通道，
 *   纳入互斥以避免截到操作中间态。
 *
 * 统计等待情况，便于可观测性暴露。
 */
object DeviceMutex {

    private val mutex = Mutex()

    private val acquiredCount = AtomicLong(0L)
    private val contentionCount = AtomicLong(0L)  // 发生过等待的次数
    private val totalWaitMs = AtomicLong(0L)

    /**
     * 在互斥保护下执行设备操作
     *
     * @param action 实际设备操作
     * @return action 的返回值
     */
    suspend fun <T> withDeviceLock(action: suspend () -> T): T {
        acquiredCount.incrementAndGet()
        // 尝试快速获取，若失败说明有竞争
        val start = System.currentTimeMillis()
        val lockedImmediately = mutex.tryLock()
        if (!lockedImmediately) {
            contentionCount.incrementAndGet()
            // 等待获取锁
            return mutex.withLock {
                totalWaitMs.addAndGet(System.currentTimeMillis() - start)
                action()
            }
        }
        try {
            totalWaitMs.addAndGet(System.currentTimeMillis() - start)
            return action()
        } finally {
            mutex.unlock()
        }
    }

    /** 加锁执行次数 */
    fun acquiredCount(): Long = acquiredCount.get()

    /** 发生竞争（需要等待）的次数 */
    fun contentionCount(): Long = contentionCount.get()

    /** 累计等待锁的时间（毫秒） */
    fun totalWaitMs(): Long = totalWaitMs.get()

    /** 导出为可观测性 Map */
    fun toMap(): Map<String, Any> = mapOf(
        "acquired_count" to acquiredCount(),
        "contention_count" to contentionCount(),
        "total_wait_ms" to totalWaitMs()
    )
}
