"""PhoneClient - 肉包手机端 HTTP API 客户端封装。

统一处理：
- 请求头注入 Authorization: Bearer <token>
- 错误响应解析（第 6 章错误码）
- 重试与超时
- 截图二进制返回处理
"""

from __future__ import annotations

import asyncio
from typing import Any

import httpx

from src.app.plugin_system.api.log_api import get_logger

logger = get_logger("phone_adapter.client")


class PhoneApiError(Exception):
    """肉包 API 返回的错误。"""

    def __init__(self, code: str, message: str, details: dict | None = None) -> None:
        self.code = code
        self.message = message
        self.details = details or {}
        super().__init__(f"[{code}] {message}")


class PhoneClient:
    """肉包手机端 HTTP API 客户端。

    所有方法返回原始 dict/bytes，不在此层做语义转换，
    便于 Tool 层按需包装。
    """

    def __init__(
        self,
        base_url: str,
        token: str,
        timeout: float = 30.0,
        retry: int = 2,
        retry_backoff: float = 1.5,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._token = token
        self._timeout = timeout
        self._retry = retry
        self._retry_backoff = retry_backoff
        # 最近一次截图的屏幕尺寸（由响应头解析后注入）
        self._last_screen_w: int = 0
        self._last_screen_h: int = 0
        self._last_image_w: int = 0
        self._last_image_h: int = 0
        self._http: httpx.AsyncClient | None = None

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        return headers

    async def _ensure_client(self) -> httpx.AsyncClient:
        if self._http is None:
            self._http = httpx.AsyncClient(
                base_url=self._base_url,
                headers=self._headers(),
                timeout=self._timeout,
            )
        return self._http

    async def close(self) -> None:
        if self._http is not None:
            await self._http.aclose()
            self._http = None

    async def _request(
        self,
        method: str,
        path: str,
        *,
        params: dict | None = None,
        json_body: dict | None = None,
    ) -> dict[str, Any]:
        """发起 JSON 请求并解析统一响应结构。

        Raises:
            PhoneApiError: 当响应 success=false 或 HTTP 非 2xx。
        """
        client = await self._ensure_client()
        attempt = 0
        last_exc: Exception | None = None
        while attempt <= self._retry:
            try:
                resp = await client.request(method, path, params=params, json=json_body)
                if resp.status_code == 401:
                    raise PhoneApiError("UNAUTHORIZED", "Token 缺失或无效")
                if resp.status_code == 429:
                    raise PhoneApiError("RATE_LIMITED", "请求过于频繁，已被限流")
                if resp.status_code >= 500:
                    raise PhoneApiError(
                        "INTERNAL_ERROR",
                        f"服务端错误: HTTP {resp.status_code}",
                    )
                payload = resp.json()
                if not payload.get("success", False):
                    err = payload.get("error", {})
                    raise PhoneApiError(
                        err.get("code", "INTERNAL_ERROR"),
                        err.get("message", "未知错误"),
                        err.get("details"),
                    )
                return payload.get("data", {})
            except PhoneApiError:
                raise
            except (httpx.RequestError, httpx.HTTPStatusError) as exc:
                last_exc = exc
                logger.warning(f"请求 {method} {path} 失败(第{attempt + 1}次): {exc}")
            except Exception as exc:
                last_exc = exc
                logger.warning(f"请求 {method} {path} 异常(第{attempt + 1}次): {exc}")

            attempt += 1
            if attempt <= self._retry:
                await asyncio.sleep(self._retry_backoff ** attempt)

        raise PhoneApiError("INTERNAL_ERROR", f"请求重试耗尽: {last_exc}")

    # ---------- 系统域 ----------

    async def ping(self) -> dict[str, Any]:
        """GET /api/system/ping，返回设备信息。"""
        return await self._request("GET", "/api/system/ping")

    async def status(self) -> dict[str, Any]:
        """GET /api/system/status，返回 Shizuku/屏幕/电量/当前 App。"""
        return await self._request("GET", "/api/system/status")

    # ---------- 设备控制域 ----------

    async def screenshot(
        self,
        fmt: str = "jpeg",
        quality: int = 70,
        scale: float = 0.5,
        return_mode: str = "binary",
    ) -> bytes | dict[str, Any]:
        """GET /api/device/screenshot。

        binary 模式返回 bytes 并更新尺寸缓存；
        base64 模式返回 dict（含 image base64 与尺寸）。
        """
        client = await self._ensure_client()
        params = {
            "format": fmt,
            "quality": str(quality),
            "scale": str(scale),
            "return": return_mode,
        }
        resp = await client.get("/api/device/screenshot", params=params)
        if resp.status_code != 200:
            raise PhoneApiError("SCREENSHOT_FAILED", f"截图失败: HTTP {resp.status_code}")

        # 解析响应头尺寸
        self._last_screen_w = int(resp.headers.get("X-Screen-Width", 0))
        self._last_screen_h = int(resp.headers.get("X-Screen-Height", 0))
        self._last_image_w = int(resp.headers.get("X-Image-Width", 0))
        self._last_image_h = int(resp.headers.get("X-Image-Height", 0))

        if return_mode == "base64":
            payload = resp.json()
            if not payload.get("success", False):
                err = payload.get("error", {})
                raise PhoneApiError(
                    err.get("code", "SCREENSHOT_FAILED"),
                    err.get("message", "截图失败"),
                )
            return payload.get("data", {})

        return resp.content

    async def tap(self, x: int, y: int, duration_ms: int = 0) -> dict[str, Any]:
        """POST /api/device/tap。"""
        return await self._request(
            "POST", "/api/device/tap", json_body={"x": x, "y": y, "duration_ms": duration_ms}
        )

    async def double_tap(self, x: int, y: int) -> dict[str, Any]:
        """POST /api/device/double_tap。"""
        return await self._request("POST", "/api/device/double_tap", json_body={"x": x, "y": y})

    async def long_press(self, x: int, y: int, duration_ms: int = 1000) -> dict[str, Any]:
        """POST /api/device/long_press。"""
        return await self._request(
            "POST",
            "/api/device/long_press",
            json_body={"x": x, "y": y, "duration_ms": duration_ms},
        )

    async def swipe(
        self,
        x1: int,
        y1: int,
        x2: int,
        y2: int,
        duration_ms: int = 300,
        steps: int = 10,
    ) -> dict[str, Any]:
        """POST /api/device/swipe。"""
        return await self._request(
            "POST",
            "/api/device/swipe",
            json_body={
                "x1": x1,
                "y1": y1,
                "x2": x2,
                "y2": y2,
                "duration_ms": duration_ms,
                "steps": steps,
            },
        )

    async def input_text(self, text: str, paste: bool = False) -> dict[str, Any]:
        """POST /api/device/input。"""
        return await self._request(
            "POST", "/api/device/input", json_body={"text": text, "paste": paste}
        )

    async def key(self, key: str) -> dict[str, Any]:
        """POST /api/device/key。key: home/back/recent/power/volume_up/volume_down/enter/delete。"""
        return await self._request("POST", "/api/device/key", json_body={"key": key})

    async def clipboard(self, action: str, text: str = "") -> dict[str, Any]:
        """POST /api/device/clipboard。action: get/set。"""
        body: dict[str, Any] = {"action": action}
        if action == "set":
            body["text"] = text
        return await self._request("POST", "/api/device/clipboard", json_body=body)

    # ---------- 应用域 ----------

    async def search_apps(self, keyword: str) -> dict[str, Any]:
        """GET /api/apps/search?keyword=。"""
        return await self._request("GET", "/api/apps/search", params={"keyword": keyword})

    async def open_app(self, query: str) -> dict[str, Any]:
        """POST /api/apps/open。query 可为包名/应用名/拼音。"""
        return await self._request("POST", "/api/apps/open", json_body={"query": query})

    async def deep_link(self, uri: str) -> dict[str, Any]:
        """POST /api/apps/deep_link。"""
        return await self._request("POST", "/api/apps/deep_link", json_body={"uri": uri})

    async def current_app(self) -> dict[str, Any]:
        """GET /api/apps/current。"""
        return await self._request("GET", "/api/apps/current")

    # ---------- 高级域 ----------

    async def shell(self, command: str, timeout_ms: int = 5000) -> dict[str, Any]:
        """POST /api/advanced/shell。服务端复用 ShellTool 安全检查。"""
        return await self._request(
            "POST",
            "/api/advanced/shell",
            json_body={"command": command, "timeout_ms": timeout_ms},
        )

    async def http_request(
        self,
        url: str,
        method: str = "GET",
        headers: dict | None = None,
        body: str | None = None,
        timeout_ms: int = 10000,
    ) -> dict[str, Any]:
        """POST /api/advanced/http。让手机本地发起 HTTP。"""
        return await self._request(
            "POST",
            "/api/advanced/http",
            json_body={
                "url": url,
                "method": method,
                "headers": headers or {},
                "body": body,
                "timeout_ms": timeout_ms,
            },
        )

