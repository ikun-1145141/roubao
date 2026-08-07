"""输入类工具 - 文本输入、系统按键、剪贴板。"""

from __future__ import annotations

from typing import Annotated

from src.app.plugin_system.api.log_api import get_logger

from .base import PhoneToolBase

logger = get_logger("phone_tool.input")


class PhoneInputTextTool(PhoneToolBase):
    """输入文本。"""

    name: str = "phone_input_text"
    description: str = (
        "在当前焦点输入框输入文本。"
        "中文建议用 paste=true 通过剪贴板粘贴，更可靠。"
    )

    async def execute(
        self,
        text: Annotated[str, "要输入的文本"],
        paste: Annotated[bool, "是否用剪贴板粘贴（输入中文更可靠），默认 false"] = False,
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            await adapter.client.input_text(text, paste)
            return True, {"executed": True, "text": text, "paste": paste}
        except Exception as exc:
            logger.error(f"输入文本失败: {exc}")
            return False, f"输入文本失败: {exc}"


class PhoneKeyTool(PhoneToolBase):
    """系统按键。"""

    name: str = "phone_key"
    description: str = (
        "按下系统按键。key 可选：home（主页）、back（返回）、recent（最近任务）、"
        "power（电源）、volume_up（音量加）、volume_down（音量减）、"
        "enter（回车）、delete（删除）。"
    )

    async def execute(
        self,
        key: Annotated[str, "按键名：home/back/recent/power/volume_up/volume_down/enter/delete"],
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            await adapter.client.key(key)
            return True, {"executed": True, "key": key}
        except Exception as exc:
            logger.error(f"按键失败: {exc}")
            return False, f"按键失败: {exc}"


class PhoneClipboardTool(PhoneToolBase):
    """读写剪贴板。"""

    name: str = "phone_clipboard"
    description: str = "读取或设置手机剪贴板内容。action=get 读取，action=set 写入。"

    async def execute(
        self,
        action: Annotated[str, "操作：get（读取）或 set（写入）"],
        text: Annotated[str, "当 action=set 时要写入的文本，get 时忽略"] = "",
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            result = await adapter.client.clipboard(action, text)
            return True, result
        except Exception as exc:
            logger.error(f"剪贴板操作失败: {exc}")
            return False, f"剪贴板操作失败: {exc}"
