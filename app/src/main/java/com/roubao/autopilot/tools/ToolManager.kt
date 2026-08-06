package com.roubao.autopilot.tools

import android.content.Context
import com.roubao.autopilot.controller.AppScanner
import com.roubao.autopilot.controller.DeviceController

/**
 * 工具管理器
 *
 * 负责初始化、注册和管理所有 Tools
 * 作为 Tool 层的统一入口
 */
class ToolManager private constructor(
    private val context: Context,
    private val deviceController: DeviceController,
    private val appScanner: AppScanner
) {

    // 持有各个工具的引用（方便直接调用）
    lateinit var searchAppsTool: SearchAppsTool
        private set
    lateinit var openAppTool: OpenAppTool
        private set
    lateinit var clipboardTool: ClipboardTool
        private set
    lateinit var deepLinkTool: DeepLinkTool
        private set
    lateinit var shellTool: ShellTool
        private set
    lateinit var httpTool: HttpTool
        private set

    // 设备控制类工具（远程受控模式新增）
    lateinit var tapTool: TapTool
        private set
    lateinit var longPressTool: LongPressTool
        private set
    lateinit var doubleTapTool: DoubleTapTool
        private set
    lateinit var swipeTool: SwipeTool
        private set
    lateinit var inputTool: InputTool
        private set
    lateinit var keyTool: KeyTool
        private set
    lateinit var screenshotTool: ScreenshotTool
        private set
    lateinit var getCurrentAppTool: GetCurrentAppTool
        private set

    /**
     * 初始化所有工具
     */
    private fun initialize() {
        // 创建工具实例
        searchAppsTool = SearchAppsTool(appScanner)
        openAppTool = OpenAppTool(deviceController, appScanner)
        clipboardTool = ClipboardTool(context)
        deepLinkTool = DeepLinkTool(deviceController)
        shellTool = ShellTool(deviceController)
        httpTool = HttpTool()

        // 设备控制类工具
        tapTool = TapTool(deviceController)
        longPressTool = LongPressTool(deviceController)
        doubleTapTool = DoubleTapTool(deviceController)
        swipeTool = SwipeTool(deviceController)
        inputTool = InputTool(deviceController)
        keyTool = KeyTool(deviceController)
        screenshotTool = ScreenshotTool(deviceController)
        getCurrentAppTool = GetCurrentAppTool(deviceController)

        // 注册到全局 Registry
        ToolRegistry.register(searchAppsTool)
        ToolRegistry.register(openAppTool)
        ToolRegistry.register(clipboardTool)
        ToolRegistry.register(deepLinkTool)
        ToolRegistry.register(shellTool)
        ToolRegistry.register(httpTool)
        ToolRegistry.register(tapTool)
        ToolRegistry.register(longPressTool)
        ToolRegistry.register(doubleTapTool)
        ToolRegistry.register(swipeTool)
        ToolRegistry.register(inputTool)
        ToolRegistry.register(keyTool)
        ToolRegistry.register(screenshotTool)
        ToolRegistry.register(getCurrentAppTool)

        println("[ToolManager] 已初始化 ${ToolRegistry.getAll().size} 个工具")
    }

    /**
     * 执行工具
     */
    suspend fun execute(toolName: String, params: Map<String, Any?>): ToolResult {
        return ToolRegistry.execute(toolName, params)
    }

    /**
     * 获取所有工具描述（给 LLM）
     */
    fun getToolDescriptions(): String {
        return ToolRegistry.getAllDescriptions()
    }

    /**
     * 获取可用工具列表
     */
    fun getAvailableTools(): List<Tool> {
        return ToolRegistry.getAll()
    }

    /**
     * 获取 DeviceController（供 server 模块查询设备状态用）
     */
    fun getDeviceController(): DeviceController = deviceController

    /**
     * 获取 AppScanner
     */
    fun getAppScanner(): AppScanner = appScanner

    /**
     * 获取 Context
     */
    fun getContext(): Context = context

    companion object {
        @Volatile
        private var instance: ToolManager? = null

        /**
         * 初始化单例
         */
        fun init(
            context: Context,
            deviceController: DeviceController,
            appScanner: AppScanner
        ): ToolManager {
            return instance ?: synchronized(this) {
                instance ?: ToolManager(context, deviceController, appScanner).also {
                    it.initialize()
                    instance = it
                }
            }
        }

        /**
         * 获取单例
         */
        fun getInstance(): ToolManager {
            return instance ?: throw IllegalStateException("ToolManager 未初始化，请先调用 init()")
        }

        /**
         * 检查是否已初始化
         */
        fun isInitialized(): Boolean = instance != null
    }
}
