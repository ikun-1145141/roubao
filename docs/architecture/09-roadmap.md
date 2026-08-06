# 第 9 章 · 开发路线图

## 9.1 里程碑总览

```mermaid
graph LR
    M0[M0 契约冻结<br/>本文档定稿] --> M1[M1 手机端 HTTP Server<br/>+ 原子接口]
    M1 --> M2[M2 MoFox phone_adapter<br/>+ 最小 Tool 集打通]
    M2 --> M3[M3 端到端联调<br/>跑通 Tool 集成]
    M3 --> M4[M4 安全加固<br/>+ 熔断 + 审计]
    M4 --> M5[M5 体验优化<br/>流式 + 区域截图 + 多手机]
```

每个里程碑均可独立验证与交付。

## 9.2 M1 · 手机端 HTTP Server

**目标**：肉包起本地 HTTP Server，暴露原子操作接口，可用 curl 调通。

**任务**

1. 新增 `server/` 目录，引入 NanoHTTPD 依赖。
2. 实现 `HttpServerService`（前台 Service），承载 Server 生命周期。
3. 实现 `ApiRouter` 路由分发 + Token 鉴权中间件。
4. 新增缺失 Tool：`TapTool` `SwipeTool` `InputTool` `KeyTool` `ScreenshotTool`，注册到 `ToolManager`。
5. 实现 Handler：`SystemHandler` `ScreenshotHandler` `TapHandler` `SwipeHandler` `InputHandler` `KeyHandler` `ClipboardHandler` `AppHandler` `ShellHandler`。
6. 设置页新增「远程受控」开关与 Token 管理。
7. 设备互斥 `Mutex` + 限流。

**验收**

```bash
curl -H "Authorization: Bearer <token>" http://<手机IP>:8765/api/system/ping
curl -H "..." http://<手机IP>:8765/api/device/screenshot -o shot.png
curl -X POST -H "..." -H "Content-Type: application/json" \
  -d '{"x":540,"y":1200}' http://<手机IP>:8765/api/device/tap
```

均返回正确结果。

**改动范围**：仅肉包侧，不动 MoFox。

## 9.3 M2 · MoFox phone_adapter + 最小 Tool 集

**目标**：MoFox 能连上肉包，LLM 能调用截图与点击。

**任务**

1. 新建 `plugins/phone_adapter/`，实现 `PhoneAdapterPlugin` + `PhoneAdapter` + `PhoneClient`（httpx）。
2. 配置项 `config/plugins/phone_adapter.toml`。
3. 启动时 ping 校验，发布 `ON_PHONE_CONNECTED` 事件。
4. 心跳定时器。
5. 新建 `plugins/phone_tool/`，实现最小 Tool 集：`phone_screenshot` `phone_tap` `phone_search_apps` `phone_open_app`。
6. Tool 通过 `adapter_api.get_adapter(...)` 获取 `PhoneAdapter` 实例。
7. 系统提示词注入工具引导。

**验收**

MoFox 控制台 `/plugins` 显示两插件 loaded，`/status` 显示 phone online；对 MoFox 说「截个图看看手机」能触发 `phone_screenshot` 并返回截图给 LLM。

**改动范围**：仅 MoFox 侧，不动肉包。

## 9.4 M3 · 端到端联调（Tool 集成打通）

**目标**：跑通一个完整的「看屏幕 -> 操作」闭环任务。

**任务**

1. 补齐 `phone_swipe` `phone_input_text` `phone_key` `phone_clipboard` `phone_deep_link` `phone_current_app` Tool。
2. 编写联调脚本：MoFox 侧用 LLM 完成「打开微信 -> 给指定联系人发消息」的完整流程。
3. 调优截图参数（quality/scale）平衡清晰度与延迟。
4. 坐标系验证：VLM 返回坐标与实际 tap 位置一致。
5. 错误处理：网络中断、Shizuku 断开的降级表现。

**验收**

用户对 MoFox 说「打开网易云音乐播放每日推荐」，MoFox 通过 phone_tool 自动完成截图-定位-点击-确认全流程，手机成功播放。

## 9.5 M4 · 安全加固

**目标**：达到可对外暴露的安全水位。

**任务**

1. 敏感包名黑名单（支付类 App 操作前拦截）。
2. `input` 关键词检测与 `403 NEED_CONFIRM` 二次确认机制。
3. 审计日志落盘 + 设置页查看。
4. 限流生效（QPS/突发/截图频率）。
5. frp TLS 加密联调。
6. 紧急停止按钮（通知栏 + 悬浮窗）。

**验收**

- 对支付类 App 执行 input 被拦截；
- 审计日志记录所有操作；
- frp 链路 `tcpdump` 抓包为密文；
- 紧急停止能立即中断任务。

## 9.6 M5 · 体验优化（后续）

按优先级排列，非承诺：

- 区域截图（仅返回指定矩形，省带宽）
- 反向 WebSocket 连接（免公网服务器）
- 多手机调度（一台 MoFox 控多台肉包）
- 截图打码（状态栏验证码区域）
- 操作录制与回放
- 与肉包现有 Skills 联动（MoFox 下发 Skill 名，肉包执行对应 Skill）
- 语音输入输出（结合 MoFox 语音能力）

## 9.8 工作量粗估

| 里程碑 | 肉包侧 | MoFox 侧 | 建议周期 |
| -------- | -------- | ---------- | ---------- |
| M0 文档定稿 | - | - | 已完成 |
| M1 HTTP Server | 较重（新模块 + 5 个 Tool） | - | 1-2 周 |
| M2 Adapter + 最小 Tool | - | 中等 | 1 周 |
| M3 端到端联调 | 轻微调优 | 补齐 Tool | 1 周 |
| M4 安全加固 | 中等 | 轻微 | 1 周 |
| M5 体验优化 | 持续 | 持续 | 滚动 |

## 9.8 风险与应对

| 风险 | 概率 | 影响 | 应对 |
| ------ | ------ | ------ | ------ |
| VLM 坐标不准 | 中 | 操作错位 | 支持归一化坐标；MoFox 侧换算；提供 tap 后截图确认 |
| Shizuku 被系统杀 | 中 | 功能不可用 | 前台通知 + 引导关闭电池优化 |
| frp 链路不稳 | 中 | 延迟高/断连 | 重连机制 + 反向 WS 备选 |
| LLM 误操作敏感功能 | 低 | 资金/隐私损失 | 熔断 + 二次确认 + 紧急停止 |
| MoFox API 版本变更 | 低 | 插件失效 | 跟随 Neo-MoFox 版本，声明 `min_core_version` |

## 9.9 本章小结

路线图按「手机端 Server -> MoFox Adapter -> 联调 -> 安全 -> 体验优化」五步推进，每步可独立交付与验证。采用单一 Tool 集成形态（Agent 全部在电脑端），用最小改动打通「电脑 AI 控制手机」的核心闭环。本文档至此结束，后续按里程碑实施时，各章节可作为开发与评审依据。
