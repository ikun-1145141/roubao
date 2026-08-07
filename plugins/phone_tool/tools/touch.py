"""触摸类工具 - 点击、双击、长按、滑动。"""

from __future__ import annotations

from typing import Annotated

from src.app.plugin_system.api.log_api import get_logger

from .base import PhoneToolBase

logger = get_logger("phone_tool.touch")


class PhoneTapTool(PhoneToolBase):
    """点击屏幕指定坐标。"""

    name: str = "phone_tap"
    description: str = (
        "点击手机屏幕指定像素坐标。"
        "坐标基于 screenshot 返回的 screen_width/screen_height（原图尺寸）。"
    )

    async def execute(
        self,
        x: Annotated[int, "像素 x 坐标"],
        y: Annotated[int, "像素 y 坐标"],
        duration_ms: Annotated[int, "按住时长（毫秒），0 表示瞬时点击"] = 0,
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            await adapter.client.tap(x, y, duration_ms)
            return True, {"executed": True, "x": x, "y": y}
        except Exception as exc:
            logger.error(f"点击失败: {exc}")
            return False, f"点击失败: {exc}"


class PhoneDoubleTapTool(PhoneToolBase):
    """双击屏幕指定坐标。"""

    name: str = "phone_double_tap"
    description: str = "双击手机屏幕指定像素坐标。"

    async def execute(
        self,
        x: Annotated[int, "像素 x 坐标"],
        y: Annotated[int, "像素 y 坐标"],
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            await adapter.client.double_tap(x, y)
            return True, {"executed": True, "x": x, "y": y}
        except Exception as exc:
            logger.error(f"双击失败: {exc}")
            return False, f"双击失败: {exc}"


class PhoneLongPressTool(PhoneToolBase):
    """长按屏幕指定坐标。"""

    name: str = "phone_long_press"
    description: str = "长按手机屏幕指定像素坐标，用于弹出菜单或拖拽。"

    async def execute(
        self,
        x: Annotated[int, "像素 x 坐标"],
        y: Annotated[int, "像素 y 坐标"],
        duration_ms: Annotated[int, "长按时长（毫秒），默认 1000"] = 1000,
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            await adapter.client.long_press(x, y, duration_ms)
            return True, {"executed": True, "x": x, "y": y, "duration_ms": duration_ms}
        except Exception as exc:
            logger.error(f"长按失败: {exc}")
            return False, f"长按失败: {exc}"


class PhoneSwipeTool(PhoneToolBase):
    """滑动屏幕。"""

    name: str = "phone_swipe"
    description: str = (
        "从 (x1,y1) 滑动到 (x2,y2)，用于滚动列表、翻页、手势操作。"
        "坐标为像素绝对坐标。"
    )

    async def execute(
        self,
        x1: Annotated[int, "起点像素 x"],
        y1: Annotated[int, "起点像素 y"],
        x2: Annotated[int, "终点像素 x"],
        y2: Annotated[int, "终点像素 y"],
        duration_ms: Annotated[int, "滑动时长（毫秒），默认 300"] = 300,
        steps: Annotated[int, "插值步数，控制平滑度，默认 10"] = 10,
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            await adapter.client.swipe(x1, y1, x2, y2, duration_ms, steps)
            return True, {
                "executed": True,
                "from": {"x": x1, "y": y1},
                "to": {"x": x2, "y": y2},
            }
        except Exception as exc:
            logger.error(f"滑动失败: {exc}")
            return False, f"滑动失败: {exc}"
