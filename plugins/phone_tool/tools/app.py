"""应用类工具 - 搜索、打开、DeepLink、当前应用。"""

from __future__ import annotations

from typing import Annotated

from src.app.plugin_system.api.log_api import get_logger

from .base import PhoneToolBase

logger = get_logger("phone_tool.app")


class PhoneSearchAppsTool(PhoneToolBase):
    """搜索已安装应用。"""

    name: str = "phone_search_apps"
    description: str = (
        "按拼音/中文/英文搜索手机已安装应用，返回包名、名称、拼音。"
        "用于在打开应用前先查找其包名。"
    )

    async def execute(
        self,
        keyword: Annotated[str, "搜索关键词（中文、拼音或英文）"],
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            result = await adapter.client.search_apps(keyword)
            return True, result
        except Exception as exc:
            logger.error(f"搜索应用失败: {exc}")
            return False, f"搜索应用失败: {exc}"


class PhoneOpenAppTool(PhoneToolBase):
    """打开应用。"""

    name: str = "phone_open_app"
    description: str = (
        "打开手机应用。query 可为应用名（如「微信」）、拼音（如 weixin）或包名。"
        "内部先搜索再打开。"
    )

    async def execute(
        self,
        query: Annotated[str, "应用名、拼音或包名"],
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            result = await adapter.client.open_app(query)
            return True, result
        except Exception as exc:
            logger.error(f"打开应用失败: {exc}")
            return False, f"打开应用失败: {exc}"


class PhoneDeepLinkTool(PhoneToolBase):
    """DeepLink 跳转。"""

    name: str = "phone_deep_link"
    description: str = (
        "通过 DeepLink URI 打开应用特定页面，"
        "如 alipays://platformapi/startapp?appId=xxx 打开支付宝特定小程序。"
    )

    async def execute(
        self,
        uri: Annotated[str, "DeepLink URI"],
    ) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            result = await adapter.client.deep_link(uri)
            return True, result
        except Exception as exc:
            logger.error(f"DeepLink 跳转失败: {exc}")
            return False, f"DeepLink 跳转失败: {exc}"


class PhoneCurrentAppTool(PhoneToolBase):
    """查询当前前台应用。"""

    name: str = "phone_current_app"
    description: str = "查询当前前台应用的包名与 Activity。"

    async def execute(self) -> tuple[bool, dict | str]:
        adapter, err = self._require_adapter()
        if adapter is None:
            return False, err
        try:
            result = await adapter.client.current_app()
            return True, result
        except Exception as exc:
            logger.error(f"查询当前应用失败: {exc}")
            return False, f"查询当前应用失败: {exc}"
