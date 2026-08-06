# 第 1 章 · 概述与目标

## 1.1 背景

肉包 (Roubao) 是一款基于视觉语言模型 (VLM) 的开源 AI 手机自动化 App，原生 Kotlin 实现，通过 Shizuku 获得系统级权限，无需电脑即可在手机本地完成「截图 → VLM 分析 → 执行 tap/swipe/type」的自动化循环。

MoFox 是一个 Python 实现的 AI 数字生命体引擎，采用 `kernel / core / app` 三层架构，具备插件系统、事件总线、LLM 调度、消息适配器等完整能力，可对接 QQ、Discord 等平台。

两者原本各自独立运行。本方案的目的是**将二者打通**：让 MoFox 成为「大脑」，肉包成为「手」，从而实现「电脑端 AI 控制手机」的完整能力链。

## 1.2 现状分析

### 肉包现状

肉包当前是**自包含**架构，VLM 调用与设备控制都在手机本地完成：

```
用户在手机输入指令
      ↓
肉包 SkillManager 意图识别
      ↓
MobileAgent 循环（本地 VLM 截图分析 + Shizuku 执行）
      ↓
任务完成
```

关键能力（来自 `app/src/main/java/com/roubao/autopilot/`）：

| 层 | 说明 | 代表组件 |
| ---- | ------ | ---------- |
| Tools | 原子操作能力 | `search_apps` `open_app` `deep_link` `clipboard` `shell` `http_request` |
| Skills | 用户意图映射 | `SkillManager` + Delegation / GUI 自动化双模式 |
| Agent | 决策循环 | `MobileAgent`（Manager/Executor/Reflector/Notetaker） |
| VLM | 视觉语言模型 | `VLMClient`（Qwen-VL / GPT-4V / Claude） |
| Control | 设备控制 | `DeviceController`（经 Shizuku UserService 执行 shell） |

### MoFox 现状

MoFox 是**中心化**框架，通过适配器 (BaseAdapter) 对接外部平台：

```
外部平台（QQ/Discord/...）
      ↕  MessageEnvelope
MoFox Core（事件总线 + 流驱动 + LLM 调度）
      ↕
插件（Tool / Action / Chatter / Agent）
```

关键扩展点：

- **BaseAdapter**: 对接外部消息平台，已有 `onebot_adapter` 实现可参考。
- **BaseTool**: 给 LLM 调用的工具，已有 `skill_manager` 的 `get_skill`/`get_reference`/`get_script` 可参考。
- **Transport**: `MessageEnvelope` 双向转换 + `CoreSink` 入站 + `MessageSender` 出站。
- **EventBus**: `ON_MESSAGE_RECEIVED` / `ON_MESSAGE_SENT` / `ON_ALL_PLUGIN_LOADED` 等钩子。

## 1.3 核心诉求

用户明确提出的目标：

1. **肉包作为手机客户端**（被控端）。
2. **MoFox 作为电脑端控制大脑**（主控端）。
3. **肉包暴露 HTTP API**，供电脑调用。
4. **内网穿透**，让电脑能访问手机（手机通常在 NAT 后）。
5. **电脑连接并控制手机**，完成「bot 玩手机」。

## 1.4 设计目标

| 目标 | 说明 |
| ------ | ------ |
| **解耦** | 肉包不依赖 MoFox 实现细节，MoFox 不依赖肉包内部类；两者仅以 HTTP + JSON 契约耦合。 |
| **Agent 集中在电脑端** | 肉包**只提供操纵手机的原子 API**，不再跑任何 Agent/VLM；决策与多步编排全部在 MoFox 侧完成。 |
| **低延迟** | 截图、操作走 HTTP 流式返回；大图片走 base64 或 binary，避免多次往返。 |
| **安全可控** | API 鉴权 + 命令白名单 + 敏感页面熔断，复用肉包已有的安全机制。 |
| **复用现有能力** | 肉包的 Tools/DeviceController/AppScanner 直接复用（Agent 层弃用）；MoFox 侧以「Adapter + Tool 插件」对接。 |
| **渐进演进** | 先打通「单步原子操作」，再支持「多步任务编排」，最后支持「全自主 Agent」。 |

## 1.5 非目标

- 不改造 MoFox 框架内核（仅在插件层扩展）。
- 不替换肉包的 Shizuku 控制层（保持其系统级权限优势）。
- 不做云 VLM 中转（VLM 仍由各端自行配置）。
- 不实现多手机集群调度（本期仅单手机单电脑）。

## 1.6 名词表

| 术语 | 含义 |
| ------ | ------ |
| 受控端 / Phone | 运行肉包 App 的 Android 手机 |
| 主控端 / Brain | 运行 MoFox 的电脑 |
| Tunnel | 内网穿透通道，把手机的本地端口暴露到公网或电脑可达地址 |
| Roubao API | 肉包暴露的 HTTP 接口集合 |
| Phone Adapter | MoFox 侧对接肉包的 BaseAdapter 插件 |
| Phone Tool | MoFox 侧暴露给 LLM 的手机操作工具插件 |
| Task | 一次完整的手机自动化任务（可含多步） |
| Step | 任务中的一个原子操作（一次截图 / 一次 tap） |

## 1.7 本章小结

本章明确了「肉包做手、MoFox 做脑、HTTP 为神经、穿透为血管」的总体定位，并划定了设计边界。后续章节将依次展开总体架构、API 设计、网络层、MoFox 集成、数据契约、安全、部署与路线图。
