# 第 5 章 · MoFox 集成方案

## 5.1 集成定位

MoFox 侧通过**两个插件**对接肉包，互不耦合：

| 插件 | 父类 | 职责 | 形态 |
|------|------|------|------|
| `phone_adapter` | `BaseAdapter` | 连接管理、鉴权、心跳、HTTP 客户端封装 | 基础设施 |
| `phone_tool` | `BaseTool` 集合 | 把手机操作暴露给 LLM 调用 | LLM 能力 |

二者关系：`phone_tool` 依赖 `phone_adapter` 提供的 HTTP 客户端，不直接发请求。这符合 MoFox「Adapter 管传输、Tool 管语义」的分层。

## 5.2 插件目录结构

参考 `plugins/skill_manager/` 与 `plugins/onebot_adapter/` 的组织方式：

```
plugins/
├── phone_adapter/
│   ├── __init__.py
│   ├── manifest.json
│   ├── plugin.py              # PhoneAdapterPlugin(BasePlugin)
│   ├── config.py              # 配置声明
│   ├── adapter.py             # PhoneAdapter(BaseAdapter)
│   └── client.py              # 同步/异步 HTTP 客户端封装
│
└── phone_tool/
    ├── __init__.py
    ├── manifest.json
    ├── plugin.py              # PhoneToolPlugin(BasePlugin)
    ├── config.py
    └── tools/
        ├── screenshot.py      # PhoneScreenshotTool
        ├── tap.py             # PhoneTapTool
        ├── swipe.py
        ├── input.py
        ├── app.py             # search/open/deep_link 合并
        └── shell.py
```

## 5.3 phone_adapter 插件

### 5.3.1 配置

`config/plugins/phone_adapter.toml`：

```toml
[connection]
base_url = "http://127.0.0.1:8765"   # 或 frp 公网地址
token = "<肉包设置页生成的 Token>"
timeout = 30                            # 秒
retry = 2
retry_backoff = 1.5

[heartbeat]
enabled = true
interval = 30                            # 秒，定时 ping
fail_threshold = 3                       # 连续失败次数后标记离线
```

> 注：肉包不是消息平台，因此**不配置** `[platform]` 段（无 `bot_id` / `bot_nickname`）。`phone_adapter` 仅作为连接与心跳管理器，所有控制走 `phone_tool` 的 Tool 调用，不经消息流。

### 5.3.2 PhoneAdapter 核心

`adapter.py`（伪代码，参照 `onebot_adapter/plugin.py` 写法）：

```python
from src.core.components.base import BaseAdapter, BasePlugin
from src.core.components.loader import register_plugin
from .client import PhoneClient

class PhoneAdapter(BaseAdapter):
    name = "phone_adapter"
    adapter_version = "0.1.0"
    platform = "phone"
    run_in_subprocess = False

    def __init__(self, core_sink, plugin=None, **kwargs):
        super().__init__(core_sink, plugin=plugin, **kwargs)
        config = plugin.config
        self.client = PhoneClient(
            base_url=config.connection.base_url,
            token=config.connection.token,
            timeout=config.connection.timeout,
        )
        self._online = False

    async def start(self):
        ok = await self.client.ping()
        self._online = ok
        if ok:
            await self._start_heartbeat()
        return ok

    async def stop(self):
        self._online = False
        await self._stop_heartbeat()

    # 注意：PhoneAdapter 不实现 _send_platform_message。
    # 肉包不是消息平台，只是被控执行器；所有控制走 phone_tool 的 Tool 调用，
    # 不经过消息流（ON_MESSAGE_RECEIVED）。
```

### 5.3.3 PhoneClient（HTTP 封装）

`client.py` 用 `httpx.AsyncClient`，统一处理：

- 请求头注入 `Authorization: Bearer <token>`
- 错误响应解析（第 6 章错误码）
- 重试与超时
- 截图二进制返回处理

```python
class PhoneClient:
    def __init__(self, base_url, token, timeout=30):
        self._http = httpx.AsyncClient(
            base_url=base_url,
            headers={"Authorization": f"Bearer {token}"},
            timeout=timeout,
        )

    async def ping(self) -> bool: ...
    async def screenshot(self, fmt="jpeg", quality=70, scale=0.5) -> bytes: ...
    async def tap(self, x, y) -> dict: ...
    async def swipe(self, x1, y1, x2, y2, duration) -> dict: ...
    async def input_text(self, text) -> dict: ...
    async def open_app(self, name_or_package) -> dict: ...
    async def search_apps(self, keyword) -> dict: ...
    async def shell(self, command) -> dict: ...
```

所有方法返回**原始 dict/bytes**，不在此层做语义转换，便于 Tool 层按需包装。

## 5.4 phone_tool 插件

### 5.4.1 设计要点

- 每个 Tool 对应肉包一个或一组接口。
- Tool 的 `description` 写清楚用途，LLM 据此选择调用。
- 截图 Tool 返回 base64 + 尺寸，供 LLM 多模态输入。
- 坐标类 Tool 接受**像素坐标**（VLM 返回像素则直接用）或**归一化坐标**（0-1，乘以屏幕尺寸转换），二选一在 Tool 描述中约定，建议起步用像素。

### 5.4.2 工具清单

参考 `skill_manager/tools.py` 的 `BaseTool` 写法：

| Tool 名 | 参数 | 返回 | 说明 |
| --------- | ------ | ------ | ------ |
| `phone_screenshot` | `format`, `quality`, `scale` | image base64 + 尺寸 | 截图，喂给 VLM |
| `phone_tap` | `x`, `y` | success | 点击 |
| `phone_double_tap` | `x`, `y` | success | 双击 |
| `phone_long_press` | `x`, `y`, `duration` | success | 长按 |
| `phone_swipe` | `x1`,`y1`,`x2`,`y2`,`duration` | success | 滑动 |
| `phone_input_text` | `text` | success | 输入文本 |
| `phone_key` | `key`(home/back/recent) | success | 系统按键 |
| `phone_clipboard` | `action`(get/set), `text` | text/success | 剪贴板 |
| `phone_search_apps` | `keyword` | app 列表 | 搜索应用 |
| `phone_open_app` | `name` | success | 打开应用 |
| `phone_deep_link` | `uri` | success | DeepLink |
| `phone_current_app` | - | 包名 | 当前前台应用 |
| `phone_shell` | `command` | stdout | 白名单 shell |

### 5.4.3 ScreenshotTool 示例

```python
from typing import Annotated
from src.core.components import BaseTool

class PhoneScreenshotTool(BaseTool):
    name = "phone_screenshot"
    description = (
        "对手机屏幕截图，返回 base64 编码的 JPEG 图片与屏幕尺寸。"
        "当需要看懂手机当前界面、寻找按钮位置时调用。"
    )

    async def execute(
        self,
        format: Annotated[str, "图片格式：jpeg 或 png，默认 jpeg"] = "jpeg",
        quality: Annotated[int, "JPEG 质量 1-100，默认 70"] = 70,
        scale: Annotated[float, "缩放 0.25-1.0，默认 0.5"] = 0.5,
    ) -> tuple[bool, dict]:
        adapter = self._get_adapter()
        data = await adapter.client.screenshot(format, quality, scale)
        import base64
        return True, {
            "image": base64.b64encode(data).decode(),
            "format": format,
            # 尺寸来自响应头，由 client 解析后注入
            "screen_width": adapter.client._last_screen_w,
            "screen_height": adapter.client._last_screen_h,
        }

    def _get_adapter(self):
        from src.app.plugin_system.api import adapter_api
        return adapter_api.get_adapter("phone_adapter:adapter:phone_adapter")
```

### 5.4.4 manifest.json

`phone_tool/manifest.json`（参考 `skill_manager/manifest.json`）：

```json
{
  "name": "phone_tool",
  "display_name": "手机控制工具",
  "version": "0.1.0",
  "description": "把肉包手机的截图、点击、输入等能力暴露给 LLM",
  "author": "Roubao Team",
  "dependencies": {
    "plugins": ["phone_adapter"],
    "components": []
  },
  "include": [
    {"component_type": "tool", "component_name": "phone_screenshot", "dependencies": []},
    {"component_type": "tool", "component_name": "phone_tap", "dependencies": []},
    {"component_type": "tool", "component_name": "phone_swipe", "dependencies": []},
    {"component_type": "tool", "component_name": "phone_input_text", "dependencies": []},
    {"component_type": "tool", "component_name": "phone_open_app", "dependencies": []},
    {"component_type": "tool", "component_name": "phone_search_apps", "dependencies": []}
  ],
  "entry_point": "plugin.py",
  "min_core_version": "1.2.0-rc.2",
  "api_version": {"adapter_api": "1.0.0"}
}
```

注意 `dependencies.plugins` 声明对 `phone_adapter` 的依赖，确保加载顺序。

## 5.5 与 MoFox 事件总线的关系

本期**不依赖**消息总线，纯 Tool 调用。但为后续扩展预留：

- `phone_adapter` 启动时发布自定义事件 `ON_PHONE_CONNECTED` / `ON_PHONE_DISCONNECTED`，供其他插件感知手机在线状态。
- `phone_tool` 在执行前后可选发布 `ON_PHONE_STEP` 事件，供日志/审计插件记录。

这些事件不进 MoFox 的消息流（`ON_MESSAGE_RECEIVED`），仅走 EventBus，避免污染聊天。

## 5.6 LLM Prompt 引导

为了让 LLM 正确使用手机工具，需在系统提示词中注入引导（参考 `skill_manager` 注入 SKILL.md 的机制）：

```
你可以通过以下工具控制一部 Android 手机：
- 先 phone_screenshot 看当前屏幕
- 用 phone_search_apps 找应用，phone_open_app 打开
- 根据屏幕内容用 phone_tap/swipe/input_text 操作
- 每次操作后建议再次 phone_screenshot 确认效果
- 坐标为像素，基于 screenshot 返回的 screen_width/height
```

这段可由 `phone_tool` 插件通过 `SystemReminderInsertType` 注入，与 `skill_manager` 同机制。

## 5.7 本章小结

MoFox 侧新增 `phone_adapter`（管连接）与 `phone_tool`（管语义）两个插件，前者封装 HTTP 客户端与心跳，后者把手机操作注册为 LLM 可调用的 Tool。Agent 全部在 MoFox 侧运行，肉包不跑任何 Agent。插件写法严格参照现有的 `onebot_adapter` 与 `skill_manager`。下一章定义两端交互的精确数据契约。
