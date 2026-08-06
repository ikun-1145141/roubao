# 第 6 章 · API 数据契约

> 本章定义肉包 HTTP API 的精确请求/响应结构，作为两端联调的契约基准。所有 JSON 字段使用 `snake_case`。

## 6.1 通用结构

### 6.1.1 成功响应

```json
{
  "success": true,
  "data": { ... },
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1722892800000
}
```

- `request_id`：可选，来自请求头 `X-Request-Id`，否则服务端生成 UUID。
- `timestamp`：毫秒级 Unix 时间戳。

### 6.1.2 错误响应

```json
{
  "success": false,
  "error": {
    "code": "SHIZUKU_UNAVAILABLE",
    "message": "Shizuku 服务未启动，请在 Shizuku App 中启动服务",
    "details": { "shizuku_available": false }
  },
  "request_id": "...",
  "timestamp": 1722892800000
}
```

### 6.1.3 错误码表

| code | HTTP | 含义 | 触发场景 |
| ------ | ------ | ------ | ---------- |
| `UNAUTHORIZED` | 401 | Token 缺失或无效 | 未带 Authorization 或 Token 校验失败 |
| `FORBIDDEN` | 403 | 命令在黑名单 | shell 命令被安全策略拒绝 |
| `NOT_FOUND` | 404 | 路径/资源不存在 | 应用未找到、路由不存在 |
| `SHIZUKU_UNAVAILABLE` | 503 | Shizuku 未启动 | 执行类接口前置检查失败 |
| `SCREENSHOT_FAILED` | 500 | 截图失败 | screencap 命令异常 |
| `INVALID_PARAMS` | 400 | 参数错误 | 缺少必填参数、坐标越界 |
| `RATE_LIMITED` | 429 | 限流 | 单 IP 超 QPS |
| `INTERNAL_ERROR` | 500 | 未知异常 | 兜底 |

## 6.2 系统域

### 6.2.1 GET /api/system/ping

**响应 data**

```json
{
  "device": "Pixel 7",
  "android_version": "14",
  "sdk": 34,
  "roubao_version": "0.1.0",
  "server_time": 1722892800000
}
```

### 6.2.2 GET /api/system/status

**响应 data**

```json
{
  "shizuku": {
    "available": true,
    "permission_level": "adb",
    "is_root": false
  },
  "screen": {
    "width": 1080,
    "height": 2400,
    "density": 420,
    "orientation": 0
  },
  "battery": {
    "level": 78,
    "charging": false
  },
  "current_app": {
    "package": "com.tencent.mm",
    "activity": "com.tencent.mm.ui.LauncherUI"
  },
  "server": {
    "uptime_seconds": 3600,
    "request_count": 152,
    "error_count": 3
  }
}
```

> 肉包只作为受控执行器，不再区分 local/remote 模式（不跑本地 Agent）。

## 6.3 设备控制域

### 6.3.1 GET /api/device/screenshot

**Query**

| 参数 | 类型 | 默认 | 约束 |
| ------ | ------ | ------ | ------ |
| format | string | png | `png` / `jpeg` |
| quality | int | 80 | 1-100，仅 jpeg |
| scale | float | 1.0 | 0.25-1.0 |
| return | string | binary | `binary` / `base64` |

**响应（binary）**：`Content-Type: image/png`，响应头带尺寸：

```
X-Screen-Width: 1080
X-Screen-Height: 2400
X-Image-Width: 540
X-Image-Height: 1200
```

**响应（base64）** `data`：

```json
{
  "image": "<base64>",
  "format": "jpeg",
  "screen_width": 1080,
  "screen_height": 2400,
  "image_width": 540,
  "image_height": 1200
}
```

### 6.3.2 POST /api/device/tap

**请求**

```json
{ "x": 540, "y": 1200, "duration_ms": 0 }
```

| 字段 | 类型 | 必填 | 说明 |
| ------ | ------ | ------ | ------ |
| x | int | 是 | 像素 x |
| y | int | 是 | 像素 y |
| duration_ms | int | 否 | 按住时长，0 表示瞬时点击 |

**响应 data**：`{ "executed": true }`

### 6.3.3 POST /api/device/swipe

```json
{
  "x1": 540, "y1": 1800,
  "x2": 540, "y2": 600,
  "duration_ms": 300,
  "steps": 10
}
```

`steps` 控制平滑度，默认 10。

### 6.3.4 POST /api/device/input

```json
{ "text": "你好世界", "paste": false }
```

`paste=true` 时直接用剪贴板粘贴（输入中文更可靠）。

### 6.3.5 POST /api/device/key

```json
{ "key": "back" }
```

`key` 枚举：`home` `back` `recent` `power` `volume_up` `volume_down` `enter` `delete`。

### 6.3.6 POST /api/device/clipboard

```json
{ "action": "set", "text": "复制内容" }
```

`action`：`get` 返回 `{ "text": "..." }`；`set` 返回 `{ "set": true }`。

## 6.4 应用域

### 6.4.1 GET /api/apps/search?keyword=weixin

**响应 data**

```json
{
  "apps": [
    {
      "package": "com.tencent.mm",
      "label": "微信",
      "pinyin": "weixin",
      "icon_base64": "<可选>"
    }
  ],
  "total": 1
}
```

复用 `AppScanner` 的拼音/语义搜索，`keyword` 支持中文、拼音、英文。

### 6.4.2 POST /api/apps/open

```json
{ "query": "微信" }
```

`query` 可以是包名、应用名、拼音，内部先 search 再 open。

**响应 data**

```json
{ "opened": true, "package": "com.tencent.mm", "label": "微信" }
```

### 6.4.3 POST /api/apps/deep_link

```json
{ "uri": "alipays://platformapi/startapp?appId=xxx" }
```

**响应 data**：`{ "launched": true }`

## 6.5 高级域

### 6.5.1 POST /api/advanced/shell

```json
{ "command": "input tap 540 1200", "timeout_ms": 5000 }
```

服务端复用 `ShellTool.checkSecurity()`，命中黑名单返回 `FORBIDDEN`。

**响应 data**

```json
{ "exit_code": 0, "stdout": "...", "stderr": "...", "duration_ms": 120 }
```

### 6.5.2 POST /api/advanced/http

让手机本地发起 HTTP（用于访问手机内网资源，如路由器管理页）。参数同肉包现有 `HttpTool`：

```json
{
  "url": "http://192.168.1.1/api/status",
  "method": "GET",
  "headers": { "Authorization": "Bearer xxx" },
  "body": null,
  "timeout_ms": 10000
}
```

**响应 data**

```json
{ "status_code": 200, "body": "...", "headers": {...} }
```

## 6.6 坐标系约定

为避免 VLM 返回坐标与屏幕不匹配，统一约定：

- **所有坐标为像素绝对坐标**，原点左上角。
- 截图经过 `scale` 缩放后，VLM 看到的是缩放图，但返回的坐标应基于**原图尺寸**（即 `screen_width/height`，非 `image_width/height`）。
- MoFox 侧若 VLM 返回归一化坐标（0-1），由 Tool 层乘以 `screen_width/height` 转换后再调 `/tap`。
- 屏幕旋转：`status` 返回 `orientation`，旋转后宽高互换，MoFox 侧需感知。

## 6.7 版本管理

- API 版本通过 URL 前缀 `/api/v1/...` 区分（起步可省略 v1，直接 `/api/...`，未来不兼容变更时引入 v2）。
- `/api/system/ping` 返回 `roubao_version`，MoFox 侧据此判断能力。
- 新增字段不视为不兼容；删除/改语义才升版本。

## 6.8 本章小结

本章定义了系统/设备/应用/高级四类接口的精确 JSON 契约与错误码。坐标统一像素绝对值，截图支持压缩与 base64。下一章讨论安全与鉴权，确保这套 API 不被滥用。
