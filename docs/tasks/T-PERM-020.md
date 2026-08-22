---
doc_type: task
id: T-PERM-020
title: 工作单 E：清理预设能力（domain_config 旧配置、PermQuery 工厂、RocketMQ 脚注、auto-grant TODO）
status: proposed
plan: docs/plans/design-review-def-followup-plan.md
domain: permission-center
design_refs:
  - docs/archive/2026-06-17/design-review.md#§11
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
  - docs/design/schema/access-service.sql
  - docs/design/architecture.md
depends_on: []
blocks: []
acceptance:
  - "执行前确认：E2 不得直接删除当前设计仍引用的 PermQuery.forValidate / forResourceCheck；如要调整，必须先替代管理校验和 auth/query-resources 设计路径"
  - "domain_config 中 SCOPE/RELATION/BINDING 旧描述完成设计取舍：删除、替换为 SUB_PERM/CLASSIFY，或明确保留理由"
  - "RocketMQ 文档只保留当前 future async event reserve 语义，不恢复旧 Topic 规划"
  - "auto-grant 维持归档评审已采纳状态：保留 TODO + Phase X 未排期；本任务只确认是否补充明显标记或拆独立跟踪任务"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-22
---

# T-PERM-020 工作单 E：清理预设能力

> 状态：proposed
> 执行门禁：进入 `in-progress` 前必须确认 E2 的替代设计；禁止把旧审计建议直接当成删除清单。

## 背景

E 来自归档设计评审 §11 的暂缓项，目标是清理过期预设、死代码和过时文档。当前核对结论是：E2 与当前权限查询管线设计直接冲突，必须先做替代设计；E3/E4 已被后续设计收敛，不应再作为开放取舍项。

## 子项核对

| 子项 | 内容 | 当前核对 | 标记 |
|---|---|---|---|
| E1 | 删除 `domain_config` 的 `SCOPE`/`RELATION`/`BINDING` 旧配置类型 | 项目级说明已采用 `CLASSIFY` 域分类模型；但 schema 和 core-flows 仍有旧配置描述，属于文档漂移 | `DESIGN_DRIFT` |
| E2 | 删除 `PermQuery.forValidate` / `forResourceCheck` | `implementation.md` 当前仍定义 `forValidate` 管理校验模式，并把 `auth/query-resources` 映射到 `PermQuery.forResourceCheck()`；两者都不能直接删除，除非先更新查询管线与调用映射设计 | `CONFLICT_REQUIRES_DECISION` |
| E3 | 清理 RocketMQ 脚注 | `architecture.md` 已收敛为“未来异步事件预留”，旧 Topic 章节已标记移除；本任务只需防止恢复旧 Topic 规划 | `NO_HARD_CONFLICT` |
| E4 | `auto-grant` TODO | 归档评审已决策“保留 TODO + Phase X”，当前 implementation/core-flows/improvement-plan 均沿用该状态；本任务不提供取消选项 | `NO_HARD_CONFLICT` |

## 执行前确认

1. E1 是否按当前域分类模型删除/替换 `SCOPE`/`RELATION`/`BINDING` 旧描述。
2. E2 若要删除 `forValidate` / `forResourceCheck`，替代后的管理校验和 `auth/query-resources` 映射分别是什么。
3. E3 默认保持“RocketMQ 未来异步事件预留”一句，不恢复旧 Topic 列表。
4. E4 默认保留 TODO + Phase X 未排期，仅确认是否拆成独立跟踪任务，还是作为本任务的文档收口项。

## 验收标准

- 所有冲突项有明确设计决策记录。
- 若修改 PermQuery 工厂，必须覆盖 `validate`、`hasPermission`、`query-resources`、`query-scopes` 等入口，不破坏统一权限查询管线。
- 清理项不得删除仍被当前 API 契约引用的能力。
- 设计回写完成后才允许任务转 `done`。
