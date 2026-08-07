"""PhoneTool 工具基类。

封装从 adapter_api 获取 PhoneAdapter 实例的通用逻辑，
所有具体工具继承此类。
"""

from __future__ import annotations

from typing import TYPE_CHECKING, cast

from src.app.plugin_system.api import adapter_api
from src.app.plugin_system.api.log_api import get_logger
from src.core.components import BaseTool

if TYPE_CHECKING:
    from plugins.phone_adapter.adapter import PhoneAdapter

logger = get_logger("phone_tool.base")

# phone_adapter 的签名格式：plugin_name:component_type:component_name
_ADAPTER_SIGNATURE = "phone_adapter:adapter:phone_adapter"


class PhoneToolBase(BaseTool):
    """手机工具基类，提供获取 PhoneAdapter 的能力。"""

    def _get_adapter(self) -> "PhoneAdapter | None":
        """获取已启动的 PhoneAdapter 实例，未启动返回 None。"""
        adapter = adapter_api.get_adapter(_ADAPTER_SIGNATURE)
        if adapter is None:
            logger.warning("phone_adapter 未启动，无法执行手机工具")
            return None
        return cast("PhoneAdapter", adapter)

    def _require_adapter(self) -> tuple["PhoneAdapter | None", str]:
        """获取 adapter，若不可用返回错误信息。"""
        adapter = self._get_adapter()
        if adapter is None:
            return None, "肉包手机适配器未启动，请先在配置中启用 phone_adapter"
        if not adapter.is_online:
            return None, "肉包手机未连接，请检查手机端服务是否运行"
        return adapter, ""
