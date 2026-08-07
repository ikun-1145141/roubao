"""截图工具 - 对手机屏幕截图，返回 base64 图片与尺寸。"""

from __future__ import annotations

import base64
from typing import Annotated

from src.app.plugin_system.api.log_api import get_logger

from .base import PhoneToolBase

logger = get_logger("phone_tool.screenshot")


class PhoneScreenshotTool(PhoneToolBase):
    """对手机屏幕截图，返回 base64 编码图片与屏幕尺寸。"""

    name: str = "phone_screenshot"
    description: str = (
        "对手机屏幕截图，返回 base64 编码的 JPEG/PNG 图片与屏幕尺寸。"
        "当需要看懂手机当前界面、寻找按钮位置时调用。"
        "坐标为像素绝对坐标，基于返回的 screen_width/screen_height。"
    )

    async def execute(
        self,
        format: Annotated[str, "图片格式：jpeg 或 png，默认 jpeg"] = "jpeg",
        quality: Annotated[int, "JPEG 质量 1-100，默认 70"] = 70,
        scale: Annotated[float, "缩放 0.25-1.0，默认 0.5（降低分辨率省带宽）"] = 0.5,
    ) -> tuple[bool, dict | str]:
        """返回 base64 图片与尺寸信息。"""
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err

        try:
            data = await adapter.client.screenshot(
                fmt=format, quality=quality, scale=scale, return_mode="binary"
            )
            if not isinstance(data, (bytes, bytearray)):
                return False, "截图返回类型异常，预期二进制数据"

            image_b64 = base64.b64encode(data).decode("ascii")
            return True, {
                "image": image_b64,
                "format": format,
                "screen_width": adapter.client._last_screen_w,
                "screen_height": adapter.client._last_screen_h,
                "image_width": adapter.client._last_image_w,
                "image_height": adapter.client._last_image_h,
            }
        except Exception as exc:
            logger.error(f"截图失败: {exc}")
            return False, f"截图失败: {exc}"
