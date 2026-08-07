"""系统与高级工具 - 设备状态、Shell 命令。"""

from __future__ import annotations

from typing import Annotated

from src.app.plugin_system.api.log_api import get_logger

from .base import PhoneToolBase

logger = get_logger("phone_tool.system")


class PhoneStatusTool(PhoneToolBase):
    """查询设备状态。"""

    name: str = "phone_status"
    description: str = (
        "查询手机设备状态：Shizuku 可用性、屏幕尺寸与方向、电量、"
        "当前前台应用、服务端运行时长与请求统计。"
    )

    async def execute(self) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            result = await adapter.client.status()
            return True, result
        except Exception as exc:
            logger.error(f"查询设备状态失败: {exc}")
            return False, f"查询设备状态失败: {exc}"


class PhoneShellTool(PhoneToolBase):
    """执行白名单 Shell 命令。"""

    name: str = "phone_shell"
    description: str = (
        "在手机上执行 Shell 命令（受服务端白名单限制，危险命令会被拒绝）。"
        "用于高级操作，如 input tap、am start、pm list 等。"
    )

    async def execute(
        self,
        command: Annotated[str, "Shell 命令，如 'input tap 540 1200'"],
        timeout_ms: Annotated[int, "超时时间（毫秒），默认 5000"] = 5000,
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            result = await adapter.client.shell(command, timeout_ms)
            return True, result
        except Exception as exc:
            logger.error(f"Shell 执行失败: {exc}")
            return False, f"Shell 执行失败: {exc}"
