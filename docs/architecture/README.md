# 肉包 × MoFox 手机控制架构文档

> **版本**: v0.1
> **日期**: 2026-08-06
> **状态**: 设计中

---

## 目录

1. [概述与目标](./01-overview.md)
2. [总体架构](./02-architecture.md)
3. [手机端 HTTP API](./03-phone-http-api.md)
4. [内网穿透网络层](./04-tunnel.md)
5. [MoFox 集成方案](./05-mofox-integration.md)
6. [API 数据契约](./06-api-contract.md)
7. [安全与鉴权](./07-security.md)
8. [部署与运维](./08-deployment.md)
9. [开发路线图](./09-roadmap.md)

---

## 文档约定

- **肉包 (Roubao)**: 指手机端的 Android App，基于 Kotlin + Shizuku，作为**被控客户端**。
- **MoFox**: 指电脑端运行的人工智能框架，作为**控制大脑**。
- **本仓库**: `g:\roubao`，肉包 Android 工程根目录。
- **参考文件**: `参考文件请勿改动上传云端/` 目录下的 MoFox 文档与 Neo-MoFox 源码，仅供阅读，不修改、不上传。

每一章独立成文件，便于增量编写与评审。建议按目录顺序阅读。
