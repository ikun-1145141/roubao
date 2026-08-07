"""PhoneAdapter 插件入口。"""

from __future__ import annotations

from src.core.components import BasePlugin, register_plugin
from src.kernel.logger import get_logger

from .adapter import PhoneAdapter
from .config import PhoneAdapterConfig

logger = get_logger("phone_adapter")


@register_plugin
class PhoneAdapterPlugin(BasePlugin):
    """肉包手机适配器插件。"""

    plugin_name: str = "phone_adapter"
    plugin_description: str = "肉包手机受控适配器（HTTP 连接管理）"
    plugin_version: str = "0.1.0"

    configs: list[type] = [PhoneAdapterConfig]

    def __init__(self, config: PhoneAdapterConfig | None = None) -> None:
        super().__init__(config)

    def get_components(self) -> list[type]:
        """返回插件组件列表。"""
        if isinstance(self.config, PhoneAdapterConfig) and not self.config.plugin.enabled:
            logger.info("phone_adapter 已在配置中禁用")
            return []
        return [PhoneAdapter]

    async def on_plugin_loaded(self) -> None:
        logger.info("phone_adapter 插件已加载")

    async def on_plugin_unloaded(self) -> None:
        logger.info("phone_adapter 插件已卸载")
