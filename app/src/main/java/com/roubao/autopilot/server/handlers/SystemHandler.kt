package com.roubao.autopilot.server.handlers

import android.os.Build
import com.roubao.autopilot.tools.ToolManager

/**
 * 系统域 Handler
 *
 * /api/system/ping
 * /api/system/status
 * /api/system/tools
 */
class SystemHandler {

    fun ping(): Map<String, Any> {
        return mapOf(
            "pong" to true,
            "timestamp" to System.currentTimeMillis()
        )
    }

    fun status(): Map<String, Any> {
        val tm = ToolManager.getInstance()
        val dc = tm.getDeviceController()
        val packageName = tm.getContext().packageName
        val screenSize = try {
            dc.getScreenSize()
        } catch (e: Exception) {
            Pair(0, 0)
        }

        return mapOf(
            "app_package" to packageName,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_int" to Build.VERSION.SDK_INT,
            "device_model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "screen_width" to screenSize.first,
            "screen_height" to screenSize.second,
            "shizuku_available" to try {
                dc.isShizukuAvailable()
            } catch (e: Exception) {
                false
            },
            "shizuku_service_bound" to try {
                dc.isAvailable()
            } catch (e: Exception) {
                false
            },
            "tools_initialized" to true,
            "server_time" to System.currentTimeMillis()
        )
    }

    companion object {
        @Volatile
        var appContext: android.content.Context? = null
    }
}
