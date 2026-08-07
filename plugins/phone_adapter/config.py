"""Phone Adapter 配置定义。"""

from __future__ import annotations

from typing import ClassVar

from src.core.components.base.config import BaseConfig, Field, SectionBase, config_section


class PhoneAdapterConfig(BaseConfig):
    """肉包手机适配器配置。"""

    name: ClassVar[str] = "config"
    description: ClassVar[str] = "肉包手机适配器配置"

    @config_section("plugin", title="插件设置", tag="plugin")
    class PluginSection(SectionBase):
        """插件基本配置。"""

        enabled: bool = Field(
            default=True,
            description="是否启用肉包手机适配器",
            label="启用适配器",
            tag="plugin",
        )
        config_version: str = Field(
            default="0.1.0",
            description="配置文件版本",
            label="配置版本",
            disabled=True,
            tag="general",
        )

    @config_section("connection", title="连接设置", tag="network")
    class ConnectionSection(SectionBase):
        """肉包手机端 HTTP Server 连接配置。"""

        base_url: str = Field(
            default="http://127.0.0.1:8765",
            description="肉包手机端 HTTP Server 地址（本地或 frp 公网地址）",
            label="服务地址",
            placeholder="http://127.0.0.1:8765",
            tag="network",
        )
        token: str = Field(
            default="",
            description="肉包设置页生成的访问 Token",
            label="访问令牌",
            input_type="password",
            placeholder="在肉包 App 设置页生成",
            tag="security",
        )
        timeout: float = Field(
            default=30.0,
            description="请求超时时间（秒）",
            label="超时时间",
            ge=1.0,
            le=300.0,
            tag="network",
        )
        retry: int = Field(
            default=2,
            description="请求失败重试次数",
            label="重试次数",
            ge=0,
            le=10,
            tag="network",
        )
        retry_backoff: float = Field(
            default=1.5,
            description="重试退避系数（指数退避）",
            label="重试退避",
            ge=1.0,
            le=5.0,
            tag="network",
        )

    @config_section("heartbeat", title="心跳设置", tag="network")
    class HeartbeatSection(SectionBase):
        """心跳检测配置。"""

        enabled: bool = Field(
            default=True,
            description="是否启用心跳检测",
            label="启用心跳",
            tag="network",
        )
        interval: float = Field(
            default=30.0,
            description="心跳间隔（秒）",
            label="心跳间隔",
            ge=5.0,
            le=600.0,
            tag="network",
        )
        fail_threshold: int = Field(
            default=3,
            description="连续失败次数后标记离线",
            label="失败阈值",
            ge=1,
            le=20,
            tag="network",
        )

    plugin: PluginSection = Field(default_factory=PluginSection)
    connection: ConnectionSection = Field(default_factory=ConnectionSection)
    heartbeat: HeartbeatSection = Field(default_factory=HeartbeatSection)
