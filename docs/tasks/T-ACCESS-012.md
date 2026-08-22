---
doc_type: task
id: T-ACCESS-012
title: 删除残留引用、回写设计并重基线任务看板
status: done
plan: docs/archive/2026-08-22/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/architecture.md
  - docs/design/project-rules.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/permission-center/overview.md
  - docs/design/permission-center/core-flows.md
  - docs/design/permission-center/implementation.md
  - docs/design/schema/access-service.sql
  - docs/archive/2026-08-22/admin-service.md
  - docs/archive/2026-08-22/schema/admin-service.sql
  - docs/archive/2026-08-22/schema/permission-center.sql
  - docs/design/README.md
  - docs/README.md
depends_on:
  - T-ACCESS-011
blocks: []
acceptance:
  - "按最终实现回写整体架构、服务设计、权限设计、核心流程、实现设计、项目规范和文档索引"
  - "access-service.sql 成为当前 schema 权威入口；旧 admin/permission schema 和服务设计按生命周期标记 superseded 或归档"
  - "T-ACCESS-002 产出 access-service.sql 后、本任务进入 in-progress 前，将该文件补入本卡 design_refs；当前不创建指向未产出文件的悬空引用"
  - "全仓扫描旧服务名、旧数据库名、旧模块路径和内部同步术语；非归档残留均删除或有明确历史说明"
  - "逐项核对 proposed 的 T-PERM/T-ADMIN 后端任务：有效任务改为 access-service 路径与新 schema，失效/重叠任务取消并完成依赖重连"
  - "更新相关 phase plan、tasks/README、plans/README、design/README、docs/README，任务状态和设计回写映射一致"
  - "依赖图无循环和 dangling，设计变更待核对项全部关闭或登记明确后续责任"
  - "计划满足归档条件；稳定结论只存在于 docs/design，计划和任务卡不形成第二套契约"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-22
---

# T-ACCESS-012 删除残留引用、回写设计并重基线任务看板

## 背景

归并会使大量旧服务路径、schema 引用和未开始任务失效。本任务负责生命周期闭环，不以简单全文替换掩盖语义变化。

## 范围

- 回写所有受影响权威设计和索引。
- 扫描并处理旧运行引用。
- 对现有未完成任务逐卡重基线、取消或重连。
- 完成计划归档前自检。

## 当前口径

- 实施中 7 项设计决策由用户拍板（2026-08-22）：architecture.md 回写并收敛重复；admin-service.md 标 superseded 归档、admin-service-api-contract.md 保留 adopted 补归并定位；T-ACCESS-013/014 迁入新计划后 merge-plan 归档；T-PERM-033 取消聚合层（页面直连 `/api/perm/*` 契约端点）；T-PERM-034 保留 perm-sdk 契约面删除断任务（执行时确认无外部消费者）；T-PERM-019 移除 D4；`.claude/rules` 编码规范重写为 access-service permission 域适用范围。另 1 项决策：T-ACCESS-011 的「CI 跑绿 40 项 Docker 门控测试」归档前置责任随归档转移至 access-post-merge-plan 准入条件。

## 验收对照

| # | 验收项 | 完成情况 |
|---|---|---|
| 1 | 设计回写 | architecture.md §1.2/§2（快照模式鉴权+统一缓存）/§3（admin 域概览+同事务本地投影，收敛指向权威文档）/§4/§6/§7 全量回写（含 Lombok 口径对齐）；admin-service-api-contract.md 补归并定位、术语映射、错误码段措辞（§1.2/§2.2/附录 B）与现行行为表述；permission-center 四件套 + v3.5-design + example-service + default-org-tree + frontend 页面设计：schema 权威统一切换、双服务叙事改单服务域口径、场景六补快照模式主链路说明；三 README + AGENTS.md 索引同步 |
| 2 | schema 生命周期 | 四份旧 DDL 与 admin-service.md（status: superseded，superseded_by=access-service-architecture）物理归档至 `docs/archive/2026-08-22/`（含批次 README）；access-service.sql 头部声明同步；全仓活引用切换 |
| 3 | design_refs 补充 | 本卡 design_refs 已含 `docs/design/schema/access-service.sql`（T-ACCESS-002 已产出） |
| 4 | 残留清理 | 主代码/配置经全量扫描零旧残留（旧数据库名/lb://旧路由/旧 Maven 模块/内部同步子系统代码 0 命中）；非归档文档 13 处 + 前端 mock/src 17 行旧 schema「权威」引用、`perm-center` 简称等 33 处活跃残留全部清理；合法保留项（LEGACY_ADMIN_SOURCE 拒绝列表值、历史切换注记、领域目录名、对外 sync API）均有明确语义 |
| 5 | 任务重基线 | T-PERM-019（移除 D4）、T-PERM-020（schema 切换）、T-PERM-021（refs 替换+F1.e 收窄外部 sync runbook）、T-PERM-033（取消聚合层，整卡重写）、T-PERM-034（裁 RoleProxyServiceImpl 失效子项、保留 SDK 删除断任务）、T-PERM-041（schema 切换）、T-ADMIN-021（去远程故障语义）、T-ADMIN-020 / T-PERM-039（看板行措辞与悬空引用修正）全部重基线；无整卡取消（所有 proposed 卡均有归并后仍成立的核心范围） |
| 6 | 索引一致 | tasks/README（状态/门禁解除/重基线行）、plans/README（merge-plan 归档+新计划+phase1 状态修正）、design/README 与 docs/README（schema 权威点名+归档记录）、AGENTS.md（当前阶段）、frontend-phase2/4、design-review-def-followup、improvement（附录 A/B）、record-level-editing-proposal 同步 |
| 7 | 依赖图 | 设计变更待核对 7 行逐项收口（旧 DDL/错误码/陈旧回填/会话边界/10+5+15/api-contract 回写/admin-permission-sync 取代）；T-ACCESS-013/014 迁入 access-post-merge-plan（plan 字段与看板行同步）；无循环、无 dangling |
| 8 | 计划归档 | merge-plan 12 任务全部 done 转 completed 并物理归档；稳定结论均落于 docs/design；「CI 跑绿 40 项 Docker 门控测试」前置按用户决策转移至新计划准入（见遗留） |

## 非目标 / 遗留

- 40 项 Docker 门控 Testcontainers 测试待 CI 环境跑绿（原 merge-plan 归档前置，已按用户决策转移至 access-post-merge-plan 准入条件；跑绿前 T-ACCESS-011 验收 2/6/9 容器部分保持条件性关闭）。
- 分支名 `feat-permission-center` 为 git 事实陈述，未随本任务处理。
- `.claude/worktrees/`（4 个本地未跟踪工作树，内含旧模块副本）与 `.zcode/` 本地会话文件属本地环境残留，未清理。
- `org-user-permission-contract.md` 变更历史行中的旧机制名（SyncTaskBuilder 等）为历史语境保留。
- T-PERM-035/036 的 design-review §11 暂缓门禁（E4/Q7-B）与归并无关，维持待 PM 重申。
