package com.roubao.autopilot.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.roubao.autopilot.R
import com.roubao.autopilot.data.SettingsManager
import com.roubao.autopilot.tools.ToolManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * HTTP Server Service（前台 Service）
 *
 * 承载 NanoHTTPD，监听端口（默认 8765），把请求转给 ApiRouter
 *
 * 启动条件：SettingsManager.remoteControlEnabled == true
 */
class HttpServerService : Service() {

    companion object {
        private const val TAG = "HttpServerService"
        private const val CHANNEL_ID = "roubao_http_server"
        private const val NOTIFICATION_ID = 0xA001

        const val ACTION_START = "com.roubao.autopilot.server.START"
        const val ACTION_STOP = "com.roubao.autopilot.server.STOP"

        /**
         * 启动服务（如果开关开启）
         */
        fun startIfEnabled(context: Context) {
            val settings = SettingsManager(context).settings.value
            if (!settings.remoteControlEnabled) return
            if (!ToolManager.isInitialized()) {
                Log.w(TAG, "ToolManager 未初始化，无法启动 HTTP Server")
                return
            }
            val intent = Intent(context, HttpServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, HttpServerService::class.java)
            context.stopService(intent)
        }
    }

    private var httpServer: HttpServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationUpdater: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("肉包受控服务运行中"))
                startServer()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 启动 HTTP Server
     */
    private fun startServer() {
        if (httpServer != null) return
        if (!ToolManager.isInitialized()) {
            Log.e(TAG, "ToolManager 未初始化，无法启动 HTTP Server")
            stopSelf()
            return
        }

        try {
            val settings = SettingsManager(this).settings.value
            val port = settings.serverPort
            val token = settings.serverToken.ifEmpty { null }

            httpServer = HttpServer(port, token).also {
                // 第二参数 daemon=true：NanoHTTPD 内部用 DefaultAsyncRunner，
                // 每个连接分配独立线程，避免单线程串行阻塞导致并发请求超时
                it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                Log.i(TAG, "HTTP Server 已启动，端口=$port，鉴权=${token != null}，多线程模式")
                updateNotification("服务运行中 :$port  ${getLocalIpAddress()}")
            }

            // 启动通知更新协程：每 5 秒刷新前台通知，显示最近连接的客户端 IP
            startNotificationUpdater(port)
        } catch (e: Exception) {
            Log.e(TAG, "启动 HTTP Server 失败", e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopServer() {
        notificationUpdater?.cancel()
        notificationUpdater = null
        try {
            httpServer?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "停止 HTTP Server 异常", e)
        }
        httpServer = null
    }

    /**
     * 启动通知更新协程
     *
     * 每 5 秒读取 ServerMetrics 的最近客户端 IP，刷新前台通知，
     * 让用户感知"谁在控制我的手机"。
     */
    private fun startNotificationUpdater(port: Int) {
        notificationUpdater = scope.launch {
            while (true) {
                delay(5000)
                val ips = ServerMetrics.recentClientIps()
                val text = if (ips.isNotEmpty()) {
                    "服务运行中 :$port  客户端: ${ips.first()}"
                } else {
                    "服务运行中 :$port  ${getLocalIpAddress()}"
                }
                updateNotification(text)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "肉包受控服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "HTTP Server 前台服务通知"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("肉包 Autopilot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    /**
     * 获取本机 IPv4 地址
     */
    private fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()?.hostAddress ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * NanoHTTPD 实现
     *
     * serve() 是同步的，但 Handler 是 suspend，用 runBlocking 桥接。
     * 父类已配置 DefaultAsyncRunner（daemon=true），每个连接独立线程，
     * 因此 serve() 内的 runBlocking 不会阻塞其他连接。
     */
    private inner class HttpServer(
        port: Int,
        private val authToken: String?
    ) : NanoHTTPD(port) {

        private val router = ApiRouter(authToken)

        override fun serve(session: IHTTPSession): Response {
            return runBlocking {
                router.route(session)
            }
        }
    }
}
