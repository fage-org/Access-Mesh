---
doc_type: task
id: T-ACCESS-027
title: 产品定位定稿回写与文档三档叙事整改（当前可用 / 已规划 / 仅演进方向）
status: done
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
  status: done
last_updated: 2026-08-28
---

# T-ACCESS-027 产品定位定稿回写与文档三档叙事整改

> 状态：done（2026-08-28 收口）

## 背景

2026-08-27 外部评审提出对外文档需按「当前可用 / 已规划 / 仅演进方向」三档分层，避免把未实现能力表述为现有能力；该整改被产品定位决策阻塞。2026-08-28 定位定案（开源通用 IAM），整改解锁。

## 范围

逐文档盘点能力声明的时态与档位，重点是根 README 特性表、architecture 能力表与模块说明、permission-center overview/implementation 中的能力描述。已有多处「未交付/规划中」标注（T-API-001 名实对齐、T-PERM-020 E3/E4 口径收口），本任务做的是统一分档收口，不是从零标注。

## 验收标准

- 三档口径有单一权威定义入口，各文档标注与其一致。
- 未实现能力无现在时态表述残留（关键词扫描：自动补全、数据权限 SQL、perm-data、RocketMQ、GROUP_ROLE 交付等）。
- 设计回写完成。

## 完成记录（2026-08-28）

- **三档口径单一权威入口**：`docs/design/README.md` 新增「产品定位与能力叙事三档口径」节——定位声明（开源通用 IAM）+ 三档定义与等价标注映射（当前可用=已实现/已交付、已规划=任务可追溯、仅演进方向=未排期设想）+ 适用范围；`docs/README.md` 权威来源表新增指引行；根 README 定位句回写。
- **现在时表述修正**：`architecture.md` §4.2 演示模块表整体重写为三档标注（原 8 模块现在时表述与 example 瘦身后事实不符——服务注册同步与单接口鉴权为当前可用，菜单/按钮/报表范围/条件/查询演示为已规划，数据权限演示与 SDK 注解式封装为仅演进方向，与 `services/example-service.md` 已交付章节对齐）；§4.3 数据库改为「当前无数据源（T-API-001 瘦身），演示库 DDL 暂无消费方保留」。
- **决策过程标注清扫**（按 2026-08-27 评审登记的存量范围）：`access-service-architecture.md` 23 处、`gateway.md` 2 处、`admin-service-api-contract.md` 1 处、看板 1 处「用户决策」→「设计定案」当前口径，三文件 last_reviewed bump 2026-08-28；`project-rules.md` 2 处为规则条文本身（定义被禁词），保留；历史已完成任务卡（合并期 T-ACCESS 旧卡等）为半归档记录，不在清扫范围（边界登记）。
- **核实无需改动**：permission-center overview/implementation（场景九禁用态声明与能力表述已合规，工厂表已随 T-PERM-020 收敛）、根 README 特性表与未交付清单（T-API-001 已对齐）、core-flows、AGENTS。
- **验收扫描（2026-08-28）**：「用户决策」活文档残留 = project-rules 规则条文 2 处（预期保留）；自动授权/数据权限/perm-data/RocketMQ/GROUP_ROLE 关键词现在时残留 = 0。纯文档任务，无代码变更、无 schema 变更，无代码回归。
