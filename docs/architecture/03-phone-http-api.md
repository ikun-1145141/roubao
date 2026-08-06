# 第 3 章 · 手机端 HTTP API

## 3.1 设计原则

肉包在手机本地起一个 HTTP Server，作为对外唯一控制入口。设计遵循：

1. **RESTful + JSON**：除截图等二进制接口外，请求/响应均为 JSON。
2. **无状态**：每个请求自包含，Server 不维护会话（会话由 MoFox 侧管理）。
3. **复用 Tool 层**：HTTP Handler 直接调用 `ToolManager.execute(name, params)`，避免重复实现。
4. **统一错误模型**：所有错误返回统一 JSON 结构（见第 6 章）。
5. **可选流式**：长任务支持 SSE / WebSocket 推送进度。

## 3.2 技术选型

| 候选 | 优点 | 缺点 | 结论 |
| ------ | ------ | ------ | ------ |
| NanoHTTPD | 轻量、纯 Java、易嵌入 | 异步支持弱 | **起步选用** |
| Ktor (CIO) | 原生协程、性能好 | 依赖体积大 | 后续可迁移 |
| Android 内置 `HttpServer` | 无 | API 受限、不稳定 | 不选 |

起步用 NanoHTTPD，单文件嵌入，端口默认 `8765`。后续若需高并发或流式，迁移到 Ktor。

## 3.3 Server 位置与生命周期

```
app/src/main/java/com/roubao/autopilot/
└── server/                      # 新增目录
    ├── HttpServerService.kt     # 前台 Service，承载 Server 生命周期
    ├── ApiRouter.kt             # 路由分发
    ├── handlers/                # 各接口 Handler
    │   ├── ScreenshotHandler.kt
    │   ├── TapHandler.kt
    │   ├── SwipeHandler.kt
    │   ├── InputHandler.kt
    │   ├── AppHandler.kt
    │   ├── ShellHandler.kt
    │   ├── TaskHandler.kt
    │   └── SystemHandler.kt
    └── dto/                     # 请求/响应数据类
```

- **以前台 Service 运行**：`HttpServerService` 继承 `Service`，启动前台通知保活，避免被系统杀死。
- **与 Shizuku 绑定联动**：Server 启动时绑定 `DeviceController`，Shizuku 断开时拒绝执行类接口。
- **开关在设置页**：用户可在设置中启停「远程受控模式」，默认关闭。

## 3.4 接口总览

按能力域分组，详细契约见第 6 章。

### 3.4.1 系统域 `/api/system`

| 方法 | 路径 | 说明 |
| ------ | ------ | ------ |
| GET | `/api/system/ping` | 心跳，返回设备信息 |
| GET | `/api/system/status` | Shizuku 状态、屏幕尺寸、电量、当前前台 App |
| POST | `/api/system/auth` | 交换/校验 Token（见第 7 章） |

### 3.4.2 设备控制域 `/api/device`

| 方法 | 路径 | 说明 |
| ------ | ------ | ------ |
| GET | `/api/device/screenshot` | 截图，返回 `image/png` 或 `image/jpeg` |
| POST | `/api/device/tap` | 点击坐标 |
| POST | `/api/device/double_tap` | 双击 |
| POST | `/api/device/long_press` | 长按 |
| POST | `/api/device/swipe` | 滑动 |
| POST | `/api/device/input` | 输入文本 |
| POST | `/api/device/key` | 按键（Home/Back/Recent） |
| POST | `/api/device/clipboard` | 读写剪贴板 |

### 3.4.3 应用域 `/api/apps`

| 方法 | 路径 | 说明 |
| ------ | ------ | ------ |
| GET | `/api/apps/list` | 列出已安装应用（分页） |
| GET | `/api/apps/search` | 按拼音/语义搜索应用（复用 `AppScanner`） |
| POST | `/api/apps/open` | 打开应用 |
| POST | `/api/apps/deep_link` | DeepLink 跳转 |
| GET | `/api/apps/current` | 当前前台应用包名 |

### 3.4.4 高级域 `/api/advanced`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/advanced/shell` | 执行白名单 Shell（复用 `ShellTool` 安全检查） |
| POST | `/api/advanced/http` | 手机本地发起 HTTP（复用 `HttpTool`，用于内网资源访问） |

## 3.5 请求/响应通用约定

### 请求头

```
Authorization: Bearer <token>
Content-Type: application/json
X-Request-Id: <uuid>      # 可选，用于链路追踪
```

### 成功响应

```json
{
  "success": true,
  "data": { ... },
  "request_id": "<uuid>",
  "timestamp": 1722892800000
}
```

### 错误响应

```json
{
  "success": false,
  "error": {
    "code": "SHIZUKU_UNAVAILABLE",
    "message": "Shizuku 服务未启动",
    "details": { ... }
  },
  "request_id": "<uuid>",
  "timestamp": 1722892800000
}
```

## 3.6 截图接口详解（最核心）

截图是被控场景调用最频繁的接口，单独说明。

**请求**

```
GET /api/device/screenshot?format=jpeg&quality=70&scale=0.5
```

| 参数 | 类型 | 默认 | 说明 |
| ------ | ------ | ------ | ------ |
| format | string | png | `png` 或 `jpeg` |
| quality | int | 80 | 仅 jpeg 有效，1-100 |
| scale | float | 1.0 | 缩放比例，0.25-1.0，降低分辨率省带宽 |
| return | string | binary | `binary` 直接返回图片；`base64` 返回 JSON 包 base64 |

**响应（binary 模式）**

```
HTTP/1.1 200 OK
Content-Type: image/jpeg
X-Screen-Width: 1080
X-Screen-Height: 2400
X-Image-Width: 540
X-Image-Height: 1200

<binary bytes>
```

屏幕尺寸通过响应头返回，MoFox 侧据此换算 VLM 返回的归一化坐标。

**响应（base64 模式）**

```json
{
  "success": true,
  "data": {
    "image": "<base64>",
    "format": "jpeg",
    "screen_width": 1080,
    "screen_height": 2400,
    "image_width": 540,
    "image_height": 1200
  }
}
```

## 3.7 与现有 Tool 的映射

每个 HTTP Handler 即「现有 Tool 的一层薄封装」，避免逻辑重复：

```
POST /api/device/tap {x, y}
      ↓ TapHandler
      ↓ 构造 params = mapOf("x" to x, "y" to y)
      ↓ toolManager.execute("tap", params)   // 复用现有 Tool
      ↓ 返回 ToolResult.toJson()
```

需要新增的 Tool（现有没有的）：

- `TapTool`（现有 `DeviceController` 有 tap 能力，但未封装为 Tool，需补）
- `SwipeTool`
- `InputTool`
- `KeyTool`
- `ScreenshotTool`

这些 Tool 新增后，HTTP Router 直接复用，无需再为本地 Agent 做适配。

## 3.8 并发与限流

- **单设备串行执行**：设备操作天然不可并行，Server 内部用一个 `Mutex` 保护所有设备控制接口，避免并发 tap 冲突。
- **截图可并发**：截图只读，允许与 tap 并行（但同一时刻只允许一个截图）。
- **限流**：单 IP 默认 10 QPS，可配置，防止恶意刷接口。

## 3.9 可观测性

- 所有请求记录日志：`method path status duration request_id`。
- `/api/system/status` 暴露计数器：总请求数、错误数、平均延迟。
- 前台通知显示当前连接的客户端 IP（便于用户感知被谁控制）。
- 可选：写入执行记录到现有「执行记录」页（与本地模式共享历史）。

## 3.10 本章小结

肉包新增 `server/` 模块，以前台 Service 承载 NanoHTTPD，暴露系统/设备/应用/高级/任务五类接口。Handler 直接复用 `ToolManager`，并补齐 `TapTool` 等缺失 Tool。截图接口支持压缩与 base64，适配 VLM 输入。下一章讨论如何把这个本地 Server 通过内网穿透暴露给电脑。
