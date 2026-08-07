"""PhoneTool 插件配置定义。"""

from __future__ import annotations

from typing import ClassVar

from src.core.components.base.config import BaseConfig, Field, SectionBase, config_section


class PhoneToolConfig(BaseConfig):
    """手机控制工具配置。"""

    name: ClassVar[str] = "config"
    description: ClassVar[str] = "手机控制工具配置"

    @config_section("plugin", title="插件设置", tag="plugin")
    class PluginSection(SectionBase):
        """插件基本配置。"""

        enabled: bool = Field(
            default=True,
            description="是否启用手机控制工具",
            label="启用工具",
            tag="plugin",
        )
        config_version: str = Field(
            default="0.1.0",
            description="配置文件版本",
            label="配置版本",
            disabled=True,
            tag="general",
        )

    @config_section("screenshot", title="截图设置", tag="general")
    class ScreenshotSection(SectionBase):
        """截图默认参数。"""

        default_format: str = Field(
            default="jpeg",
            description="默认图片格式",
            label="默认格式",
            input_type="select",
            choices=["jpeg", "png"],
            tag="general",
        )
        default_quality: int = Field(
            default=70,
            description="默认 JPEG 质量 1-100",
            label="默认质量",
            ge=1,
            le=100,
            tag="general",
        )
        default_scale: float = Field(
            default=0.5,
            description="默认缩放比例 0.25-1.0",
            label="默认缩放",
            ge=0.25,
            le=1.0,
            tag="general",
        )

    @config_section("prompt", title="提示词注入", tag="general")
    class PromptSection(SectionBase):
        """LLM 引导提示词注入。"""

        inject_actor_reminder: bool = Field(
            default=True,
            description="是否注入 actor system reminder 引导 LLM 使用手机工具",
            label="注入提示词",
            tag="general",
        )

    plugin: PluginSection = Field(default_factory=PluginSection)
    screenshot: ScreenshotSection = Field(default_factory=ScreenshotSection)
    prompt: PromptSection = Field(default_factory=PromptSection)
