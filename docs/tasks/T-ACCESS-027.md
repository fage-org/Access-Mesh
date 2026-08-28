---
doc_type: task
id: T-ACCESS-027
title: 产品定位定稿回写与文档三档叙事整改（当前可用 / 已规划 / 仅演进方向）
status: proposed
plan: docs/plans/product-positioning-landing-plan.md
domain: cross-service
design_refs:
  - README.md
  - docs/README.md
  - docs/design/README.md
  - docs/design/architecture.md
  - docs/design/access-service-architecture.md
  - docs/design/permission-center/overview.md
  - docs/design/permission-center/implementation.md
depends_on: []
blocks: []
acceptance:
  - "定位口径（开源通用 IAM）写入文档入口（docs/README.md 阅读顺序区或 design/README.md），作为叙事分档依据"
  - "对外能力声明逐项可归档：当前可用 / 已规划 / 仅演进方向；未实现能力不得以现在时态表述"
  - "重点核对清单：自动授权（预留禁用）、动态数据权限（延后 example-service）、PERSONAL/GROUP_ROLE 角色类型（建模未交付）、数据权限参考实现 perm-data（演进方向未提供模块）、RocketMQ（预留未启用）、SDK 接口扫描/自动注册（未实现，T-API-001 名实对齐口径）"
  - "纯文档任务，不改代码、不改 schema"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-28
---

# T-ACCESS-027 产品定位定稿回写与文档三档叙事整改

> 状态：proposed（2026-08-28 立项，定位口径已定案：开源通用 IAM）

## 背景

2026-08-27 外部评审提出对外文档需按「当前可用 / 已规划 / 仅演进方向」三档分层，避免把未实现能力表述为现有能力；该整改被产品定位决策阻塞。2026-08-28 定位定案（开源通用 IAM），整改解锁。

## 范围

逐文档盘点能力声明的时态与档位，重点是根 README 特性表、architecture 能力表与模块说明、permission-center overview/implementation 中的能力描述。已有多处「未交付/规划中」标注（T-API-001 名实对齐、T-PERM-020 E3/E4 口径收口），本任务做的是统一分档收口，不是从零标注。

## 验收标准

- 三档口径有单一权威定义入口，各文档标注与其一致。
- 未实现能力无现在时态表述残留（关键词扫描：自动补全、数据权限 SQL、perm-data、RocketMQ、GROUP_ROLE 交付等）。
- 设计回写完成。
