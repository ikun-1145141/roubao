"""PhoneAdapter - 肉包手机受控适配器。

肉包不是消息平台，只是被控执行器；所有控制走 phone_tool 的 Tool 调用，
不经过消息流（ON_MESSAGE_RECEIVED）。因此本适配器：
- 不实现 _send_platform_message
- from_platform_message / get_bot_info 返回占位
- 仅负责连接管理、鉴权、心跳
"""

from __future__ import annotations

import asyncio
from typing import Any

from mofox_wire import CoreSink, MessageEnvelope

from src.app.plugin_system.api.log_api import get_logger
from src.core.components.base import BaseAdapter, BasePlugin
from src.kernel.concurrency import get_task_manager

from .client import PhoneApiError, PhoneClient

logger = get_logger("phone_adapter")


class PhoneAdapter(BaseAdapter):
    """肉包手机适配器 - 管理与肉包手机端 HTTP Server 的连接与心跳。"""

    name = "phone_adapter"
    adapter_version = "0.1.0"
    description = "肉包手机受控适配器（HTTP 连接管理）"
    platform = "phone"

    run_in_subprocess = False

    def __init__(
        self,
        core_sink: CoreSink,
        plugin: BasePlugin | None = None,
        **kwargs: Any,
    ) -> None:
        super().__init__(core_sink, plugin=plugin, **kwargs)

        config = plugin.config if plugin else None
        conn = getattr(config, "connection", None)
        hb = getattr(config, "heartbeat", None)

        base_url = getattr(conn, "base_url", "http://127.0.0.1:8765")
        token = getattr(conn, "token", "")
        timeout = getattr(conn, "timeout", 30.0)
        retry = getattr(conn, "retry", 2)
        retry_backoff = getattr(conn, "retry_backoff", 1.5)

        self.client = PhoneClient(
            base_url=base_url,
            token=token,
            timeout=timeout,
            retry=retry,
            retry_backoff=retry_backoff,
        )

        self._heartbeat_enabled = getattr(hb, "enabled", True)
        self._heartbeat_interval = getattr(hb, "interval", 30.0)
        self._fail_threshold = getattr(hb, "fail_threshold", 3)

        self._online = False
        self._fail_count = 0
        self._heartbeat_task: Any | None = None

    async def on_adapter_loaded(self) -> None:
        """适配器加载：尝试 ping 并启动心跳。"""
        logger.info("肉包手机适配器正在启动...")
        try:
            info = await self.client.ping()
            self._online = True
            self._fail_count = 0
            logger.info(
                "肉包已连接: %s (Android %s, roubao %s)",
                info.get("device", "unknown"),
                info.get("android_version", "?"),
                info.get("roubao_version", "?"),
            )
        except Exception as exc:
            self._online = False
            logger.warning(f"肉包连接失败（心跳将重试）: {exc}")

        if self._heartbeat_enabled:
            tm = get_task_manager()
            self._heartbeat_task = tm.create_task(
                self._heartbeat_loop(),
                name="phone_adapter_heartbeat",
                daemon=True,
            )

    async def on_adapter_unloaded(self) -> None:
        """适配器卸载：停止心跳并关闭客户端。"""
        logger.info("肉包手机适配器正在关闭...")
        self._online = False
        if self._heartbeat_task is not None:
            self._heartbeat_task.cancel()
            self._heartbeat_task = None
        await self.client.close()

    async def _heartbeat_loop(self) -> None:
        """定时 ping，连续失败达阈值后标记离线。"""
        while True:
            try:
                await asyncio.sleep(self._heartbeat_interval)
                await self.client.ping()
                if not self._online:
                    logger.info("肉包心跳恢复，标记为在线")
                self._online = True
                self._fail_count = 0
            except asyncio.CancelledError:
                break
            except Exception as exc:
                self._fail_count += 1
                if self._fail_count >= self._fail_threshold and self._online:
                    self._online = False
                    logger.warning(
                        "肉包心跳连续失败 %d 次，标记为离线: %s",
                        self._fail_count,
                        exc,
                    )

    async def health_check(self) -> bool:
        """健康检查：返回在线状态。"""
        return self._online

    @property
    def is_online(self) -> bool:
        """肉包是否在线。"""
        return self._online

    # ---------- 以下为 BaseAdapter 抽象方法的占位实现 ----------
    # 肉包不是消息平台，不接收/发送消息，仅作为受控执行器。

    async def from_platform_message(self, raw: Any) -> MessageEnvelope:
        """肉包不产生消息流，此方法不会被调用。"""
        raise NotImplementedError("phone_adapter 不接收平台消息")

    async def _send_platform_message(self, envelope: MessageEnvelope) -> Any:
        """肉包不接收回复消息，此方法不会被调用。"""
        raise NotImplementedError("phone_adapter 不发送平台消息")

    async def get_bot_info(self) -> dict[str, Any]:
        """返回肉包设备信息作为 bot 信息。"""
        if self._online:
            try:
                return await self.client.ping()
            except Exception:
                pass
        return {"bot_id": "roubao", "bot_name": "肉包", "platform": self.platform}

