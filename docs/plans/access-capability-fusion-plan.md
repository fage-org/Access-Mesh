---
doc_type: plan
title: access-service 能力包融合
status: proposed
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md
  - docs/design/access-service-architecture.md
tasks:
  - T-ACCESS-032
  - T-ACCESS-033
  - T-ACCESS-034
  - T-ACCESS-035
  - T-ACCESS-036
  - T-ACCESS-037
  - T-ACCESS-038
  - T-ACCESS-039
  - T-ACCESS-040
  - T-ACCESS-041
acceptance: "验收五条全达成（capability-structure §1）；全部任务 done；全量回归（-T 1C 含 E2E）绿；定案修订对照（capability-structure §9）全部落地"
last_updated: 2026-09-13
---

# access-service 能力包融合

> 状态：proposed
> 来源：2026-09-13 用户逐项拍板（定案与两轮外评处置记录见 decision-registry 同日行）；目标设计 [capability-structure.md](../design/access-service-capability-structure.md)（draft，T-ACCESS-032 完成细化后转 adopted）

## 目标

消灭 admin/permission 两域平行结构：能力包终态（12 能力包 + sync 通道包 + 引擎子系统 + infrastructure）+ 平行设施归零（错误码/缓存目录/操作码各一册）+ 概念单点收口（操作码 USER 细粒度化、字段消减、system_config 单入口），对外表面基本维持现状。验收五条见设计 §1。

## 非目标

- 不合并六对双轨表、不改变引擎零直读 sys_* 不变量（设计 §4）。
- 不统一 URL 风格（登记为已知问题，docs/pending-problems.md Q-001）、不重排错误码编号段、不重组前端信息架构（设计 §6）。
- 不动任务 ID 前缀与历史档案。
- 不新增业务功能。

## 准入条件

- T-ACCESS-032 设计定稿（capability-structure 转 adopted + 架构文档修订回写）后，T-ACCESS-033 及后续任务方可进入 in-progress。

## 任务清单

| ID | 标题 | 依赖 | 状态 |
|---|---|---|---|
| [T-ACCESS-032](../tasks/T-ACCESS-032.md) | 融合设计定稿——归属清单与边界断言 | — | ⚙️ |
| [T-ACCESS-033](../tasks/T-ACCESS-033.md) | 机械迁移——能力包搬包 + 命名收敛 + 边界测试重建 | 032 | ⚙️ |
| [T-ACCESS-034](../tasks/T-ACCESS-034.md) | 操作码合一与 USER 轨细粒度化 | 033, 040 | ⚙️ |
| [T-ACCESS-035](../tasks/T-ACCESS-035.md) | 双轨死字段消减（无契约联动四项） | 033 | ⚙️ |
| [T-ACCESS-036](../tasks/T-ACCESS-036.md) | resource_entity.sort_order 退役 | 033, 040 | ⚙️ |
| [T-ACCESS-037](../tasks/T-ACCESS-037.md) | system_config 单入口化（admin /config 退役） | 033, 040 | ⚙️ |
| [T-ACCESS-038](../tasks/T-ACCESS-038.md) | 错误码合类不合号 | 033 | ⚙️ |
| [T-ACCESS-039](../tasks/T-ACCESS-039.md) | 缓存目录合一 | 033, 034 | ⚙️ |
| [T-ACCESS-040](../tasks/T-ACCESS-040.md) | API 契约深合一与设计文档重组 | 033 | ⚙️ |
| [T-ACCESS-041](../tasks/T-ACCESS-041.md) | 规则与技能文件重写（能力 + 引擎口径） | 033, 034~039, 040 | ⚙️ |

## 建议执行顺序

1. `T-ACCESS-032` 设计定稿（全部任务的前置）。
2. `T-ACCESS-033` 机械迁移：一个任务纯机械搬包 + import + 命名收敛，零业务逻辑改动；契约测试与双实例验收纪律沿用 T-ACCESS-001~012 剧本。
3. 迁移后收敛批次：`T-ACCESS-040` 契约深合一先行（034/036/037/041 的契约与引用回写落新册，避免旧册双写）。
4. 收敛项并行推进：`T-ACCESS-034` 操作码（唯一权限语义变更）、`035/036` 字段消减、`037` system_config、`038` 错误码（互不阻塞；034/036/037 等 040 完成）；`039` 缓存目录在 034 之后（目录合并以死条目已删为前提）。
5. `T-ACCESS-041` 规则/技能/索引重写收尾（依赖全部收敛任务 034~039，收尾位）。

## 归档条件

- tasks 清单全部 done（或 cancelled）；
- 验收五条达成、定案修订对照（设计 §9）全部落地；
- capability-structure 为 adopted 且架构文档修订完成。

## 当前进度

- 2026-09-13：计划建立——设计稿 draft + 十张任务卡 proposed + decision-registry 定案登记 + 三处索引刷新。
