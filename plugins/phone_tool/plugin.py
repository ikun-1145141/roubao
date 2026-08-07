"""PhoneTool 插件入口。

把肉包手机的截图、点击、输入等能力注册为 LLM 可调用的 Tool，
并通过 system reminder 注入引导提示词。
"""

from __future__ import annotations

from src.core.components import BasePlugin, register_plugin
from src.core.prompt import SystemReminderInsertType
from src.kernel.logger import get_logger

from .config import PhoneToolConfig
from .tools.app import (
    PhoneCurrentAppTool,
    PhoneDeepLinkTool,
    PhoneOpenAppTool,
    PhoneSearchAppsTool,
)
from .tools.input import PhoneClipboardTool, PhoneInputTextTool, PhoneKeyTool
from .tools.screenshot import PhoneScreenshotTool
from .tools.system import PhoneShellTool, PhoneStatusTool
from .tools.touch import (
    PhoneDoubleTapTool,
    PhoneLongPressTool,
    PhoneSwipeTool,
    PhoneTapTool,
)

logger = get_logger("phone_tool")

# 注入给 LLM 的手机工具使用引导
_PHONE_TOOL_REMINDER = """## 手机控制工具使用指南

你可以通过以下工具控制一部 Android 手机（肉包）：

### 工作流程
1. 先 `phone_screenshot` 截图看当前屏幕
2. 用 `phone_search_apps` 查找应用，`phone_open_app` 打开
3. 根据屏幕内容用 `phone_tap`/`phone_swipe`/`phone_input_text` 操作
4. 每次操作后建议再次 `phone_screenshot` 确认效果

### 坐标约定
- 所有坐标为**像素绝对坐标**，原点左上角
- 基于 `phone_screenshot` 返回的 `screen_width`/`screen_height`（原图尺寸）
- 不要使用缩放后的 `image_width`/`image_height` 作为坐标基准

### 可用工具
- `phone_screenshot`: 截图，返回 base64 图片与屏幕尺寸
- `phone_tap`/`phone_double_tap`/`phone_long_press`: 点击/双击/长按坐标
- `phone_swipe`: 滑动（滚动列表、翻页、手势）
- `phone_input_text`: 输入文本（中文建议 paste=true）
- `phone_key`: 系统按键（home/back/recent 等）
- `phone_clipboard`: 读写剪贴板
- `phone_search_apps`/`phone_open_app`/`phone_deep_link`: 应用管理
- `phone_current_app`: 查询当前前台应用
- `phone_status`: 查询设备状态（Shizuku/屏幕/电量）
- `phone_shell`: 执行白名单 Shell 命令（高级）
"""


@register_plugin
class PhoneToolPlugin(BasePlugin):
    """手机控制工具插件。"""

    plugin_name: str = "phone_tool"
    plugin_description: str = "把肉包手机操作暴露给 LLM 调用"
    plugin_version: str = "0.1.0"

    configs: list[type] = [PhoneToolConfig]

    def __init__(self, config: PhoneToolConfig | None = None) -> None:
        super().__init__(config)

    def get_components(self) -> list[type]:
        """返回所有工具组件。"""
        if isinstance(self.config, PhoneToolConfig) and not self.config.plugin.enabled:
            logger.info("phone_tool 已在配置中禁用")
            return []
        return [
            PhoneScreenshotTool,
            PhoneTapTool,
            PhoneDoubleTapTool,
            PhoneLongPressTool,
            PhoneSwipeTool,
            PhoneInputTextTool,
            PhoneKeyTool,
            PhoneClipboardTool,
            PhoneSearchAppsTool,
            PhoneOpenAppTool,
            PhoneDeepLinkTool,
            PhoneCurrentAppTool,
            PhoneStatusTool,
            PhoneShellTool,
        ]

    async def on_plugin_loaded(self) -> None:
        """插件加载：注入 system reminder。"""
        logger.info("phone_tool 插件已加载")
        self._sync_system_reminder()

    async def on_plugin_unloaded(self) -> None:
        """插件卸载：清理 system reminder。"""
        logger.info("phone_tool 插件已卸载")
        from src.core.prompt import get_system_reminder_store

        store = get_system_reminder_store()
        store.delete("actor", "phone_tool_guide")
        store.delete("sub_actor", "phone_tool_guide")

    def _sync_system_reminder(self) -> None:
        """注入手机工具使用引导到 actor/sub_actor。"""
        from src.core.prompt import get_system_reminder_store

        inject = True
        if isinstance(self.config, PhoneToolConfig):
            inject = self.config.prompt.inject_actor_reminder

        store = get_system_reminder_store()
        if inject:
            store.set(
                "actor",
                name="phone_tool_guide",
                content=_PHONE_TOOL_REMINDER,
                insert_type=SystemReminderInsertType.DYNAMIC,
            )
            store.set(
                "sub_actor",
                name="phone_tool_guide",
                content=_PHONE_TOOL_REMINDER,
                insert_type=SystemReminderInsertType.DYNAMIC,
            )
        else:
            store.delete("actor", "phone_tool_guide")
            store.delete("sub_actor", "phone_tool_guide")
