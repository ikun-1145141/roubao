# 第 7 章 · 安全与鉴权

## 7.1 威胁模型

肉包暴露 HTTP API 后，手机变成「可被远程控制的设备」，风险显著上升。需防范：

| 威胁 | 场景 | 影响 |
| ------ | ------ | ------ |
| 未授权访问 | 端口被扫描，他人直接调 API | 手机被任意操作 |
| Token 泄露 | frp 配置文件、MoFox 日志泄露 Token | 同上 |
| 恶意指令 | LLM 幻觉或被 prompt injection 诱导执行危险操作 | 误删数据、转账、发诈骗消息 |
| 命令注入 | `/shell` 接口被构造恶意命令 | 系统破坏 |
| 敏感页面操作 | 自动操作到支付/密码页面 | 资金损失 |
| 流量耗尽 | 高频截图刷接口 | 4G 流量与电量耗尽 |
| 中间人 | 穿透链路被窃听 | Token 与截图泄露 |

## 7.2 鉴权机制

### 7.2.1 Token 生成与存储

- 肉包设置页提供「生成 Token」按钮，生成 32 字节随机串（`SecureRandom`），Base64URL 编码。
- Token 用现有 `SettingsManager` 的 AES-256-GCM 加密存储（与 API Key 同机制）。
- 支持多个 Token（便于区分 MoFox 实例与调试设备），每个 Token 可命名、可吊销。

### 7.2.2 请求鉴权

所有接口（除 `/api/system/ping` 可选）必须带：

```
Authorization: Bearer <token>
```

服务端校验流程：

1. 提取 Token，与存储的 Token 列表比对。
2. 不匹配返回 `401 UNAUTHORIZED`。
3. 匹配则记录调用方标识（Token 名称）到日志。

### 7.2.3 可选：mTLS / 客户端证书

高安全场景可在 frp 层启用 mTLS，仅持有证书的客户端可连接。本期不实现，文档预留。

## 7.3 命令安全（复用现有机制）

肉包 `ShellTool` 已有完善的安全策略，HTTP `/api/advanced/shell` 直接复用：

- **黑名单**（来自 `ShellTool.BASE_BLOCKED_COMMANDS`）：`rm -rf` `format` `mkfs` `dd if=` `reboot` `shutdown` `> /dev` `chmod 777 /`。
- **白名单前缀**（`ALLOWED_PREFIXES`）：`input` `am` `pm` `wm` `screencap` `monkey` `dumpsys` `getprop` `settings` `content` `cmd` `ls` `cat` `echo`。
- **su -c**：默认禁用，需在设置开启 Root 模式 + 允许 su -c。

HTTP 层额外限制：

- `/shell` 默认**关闭**，需在设置显式开启「允许远程 Shell」。
- 即便开启，仍受黑白名单约束。

## 7.4 敏感页面熔断

复用肉包现有的安全保护机制，并增强：

1. **执行前截图检测**：每次 `tap`/`input`/`swipe` 前，可选先截图，用本地轻量规则匹配（OCR 或包名）判断是否进入敏感页面。
2. **敏感包名黑名单**：当前前台 App 为支付类（支付宝、微信支付、银行 App）时，拒绝 `input` 含数字/密码特征的操作。
3. **关键词检测**：`input` 文本含「密码」「验证码」「转账」等关键词时，要求二次确认（返回 `403 NEED_CONFIRM`，MoFox 侧需用户在聊天中确认）。
4. **手动停止**：肉包悬浮窗/通知保留「紧急停止」按钮，一键断开所有连接并停止任务。

> 起步只实现第 4 点（紧急停止）+ 包名黑名单（硬编码常见支付包），其余列为 v2。

## 7.5 限流与配额

| 维度 | 默认 | 配置项 |
| ------ | ------ | -------- |
| 单 IP QPS | 10 | `server.rate_limit_qps` |
| 单 IP 突发 | 20 | `server.rate_limit_burst` |
| 截图频率 | 2/秒 | 防止刷流量 |
| 任务并发 | 1 | 设备互斥 |
| 每日总请求数 | 无限 | 可选开启 |

超限返回 `429 RATE_LIMITED`，响应头 `Retry-After: <秒>`。

## 7.6 传输安全

| 场景 | 方案 |
| ------ | ------ |
| 局域网直连 | HTTP + Token（信任内网）；可选自签 HTTPS |
| frp 中转 | frp 启用 `tls.enable = true`，链路加密 |
| Cloudflare | 自动 HTTPS |
| 反向 WS（v2） | `wss://` + Token |

**截图隐私**：截图可能含敏感信息（聊天记录、验证码）。建议：

- MoFox 侧不持久化截图，用完即丢。
- 肉包日志不记录截图内容，仅记录「截图调用次数」。
- 可选「截图打码」：对状态栏（含验证码短信通知区域）打码，列为 v2。

## 7.7 审计日志

肉包记录所有受控操作，便于事后追溯：

```
[2026-08-06 10:23:15] INFO  client=mofox-home method=POST path=/api/device/tap params={"x":540,"y":1200} result=success duration=85ms
[2026-08-06 10:23:18] WARN  client=mofox-home method=POST path=/api/advanced/shell command="rm -rf /data" result=forbidden
```

- 日志存于 `/data/data/com.roubao.autopilot/files/audit/`，按天滚动。
- 设置页可查看最近 100 条审计记录。
- 可选导出日志文件。

## 7.8 MoFox 侧安全

MoFox 作为主控端也有责任：

1. **Token 存储**：MoFox 配置文件中的 Token 设置文件权限 `600`，不进版本库。
2. **LLM 指令过滤**：在 `phone_tool` 的 Tool 层对 LLM 传入的参数做二次校验（如 `input_text` 长度限制、`shell` 命令再过一遍黑名单），不完全信任 LLM 输出。
3. **操作确认**：高危操作（`shell`、大额 `input`）可选要求用户在 MoFox 聊天中确认后才下发。
4. **会话隔离**：不同用户触发的手机任务用不同 `request_id` 隔离，避免结果串台。

## 7.9 安全配置汇总

`config/server.toml`（肉包侧，新增）：

```toml
[server]
enabled = false               # 默认关闭，需用户显式开启
port = 8765
host = "0.0.0.0"              # 直连需 0.0.0.0；仅本地用 127.0.0.1

[auth]
tokens = []                   # 加密存储，设置页管理
require_auth = true

[rate_limit]
qps = 10
burst = 20
screenshot_qps = 2

[security]
allow_remote_shell = false
sensitive_packages = [
  "com.eg.android.AlipayGphone",
  "com.tencent.mm",
  "com.tencent.mobileqq"
]
emergency_stop_enabled = true

[audit]
enabled = true
max_entries = 10000
```

## 7.10 本章小结

安全以「Token 鉴权 + 命令黑白名单 + 敏感页面熔断 + 限流 + 审计」五层防御。复用肉包 `ShellTool` 已有安全逻辑，新增 HTTP 层鉴权与限流。MoFox 侧对 LLM 输出做二次校验，不完全信任模型。下一章讨论部署与日常运维。
