---
doc_type: plan
title: 当前候选版本独立环境部署与首次接入验收
status: archived
domain: cross-service
design_refs:
  - docs/design/architecture.md
  - docs/design/tenant-lifecycle.md
  - docs/design/browser-session-authentication.md
  - docs/design/frontend/file-management.md
  - docs/design/extension-guide.md
  - docs/design/services/example-service.md
  - docs/design/service-authentication.md
  - docs/design/access-service-api-contract.md#operation-admission-protocol
  - docs/design/access-service-rebuild-runbook.md
tasks:
  - T-ACCESS-131
  - T-ACCESS-132
  - T-ACCESS-133
  - T-ACCESS-134
  - T-ACCESS-135
acceptance: 固定候选产物在隔离环境完成部署、租户使用、首次业务接入与恢复验收；阻断项处置、完整回归、设计回写及证据核对完成
last_updated: 2026-10-18
---

# 当前候选版本独立环境部署与首次接入验收

## 目标

验证现有产品能否从固定版本构建产物，依照公开使用文档完成独立部署、首次接入和恢复，形成可复现的候选版本验收结论。行为契约沿用 design_refs；详细范围与验收条件只在任务卡维护。

## 非目标

不引入新业务系统、通用 SQL 自动改写、数据库迁移框架、多实例文件存储或生产容量承诺；不启动 T-PERM-036、Q-060/Q-062/Q-065/Q-068。沿已交付路径修正文档不等于启动剩余契约全面补册。不自动创建发布标签、推送、发布 Release 或部署现有业务环境，不自动串联外部 AI 评审。

## 准入条件

本计划已获准执行，由 T-ACCESS-131 固定候选版本和环境清单；默认采用本机独立容器栈、回环访问和全新专用数据资源。若执行环境不足，记录实际缺口，不借用现有开发或业务实例冒充隔离环境。外部主机及公网部署需要另行明确目标和访问条件，验收结论不得扩展到未验证形态。

实施按任务范围加载安全、测试、前后端及权限规则；事实性阻断按既有契约最小修复，产品语义变化或暂缓项重启先按决策提问协议处理。计划开始执行后转 active，任务进入实施时再改 in-progress。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-131](tasks/T-ACCESS-131.md) | 候选版本冻结与隔离部署 | ✅ | — |
| [T-ACCESS-132](tasks/T-ACCESS-132.md) | 租户与管理台使用验收 | ✅ | T-ACCESS-131 |
| [T-ACCESS-133](tasks/T-ACCESS-133.md) | 首次业务接入与授权闭环验收 | ✅ | T-ACCESS-132 |
| [T-ACCESS-134](tasks/T-ACCESS-134.md) | 重启持久化与备份恢复验收 | ✅ | T-ACCESS-133 |
| [T-ACCESS-135](tasks/T-ACCESS-135.md) | 候选版本验收收口 | ✅ | T-ACCESS-134 |

## 归档条件

全部任务达到终态并满足计划验收；取消验收任务不得等同其验收通过。必要场景未执行或存在阻断时不输出候选版本通过结论。验收只对 T-ACCESS-135 确认的最终版本、产物及实际拓扑成立。

任务完成前回写引用设计的实际交付范围，并同步 quickstart、部署手册和接入指南中与本计划相关的事实。完成本地代码轨及文档轨核对；收口回归、证据格式、版本变动后的重新验证责任由 T-ACCESS-135 承载。

计划完成后按生命周期规范，将计划、任务及附属证据一起归档，更新 docs、plans、tasks 索引；暂缓事项保持原载体与状态。

## 当前进度

T-ACCESS-131～T-ACCESS-135 全部完成，候选本机隔离部署、使用、接入、同版本恢复与完整回归通过；最终结果以任务卡及验收证据为准。源环境、独立恢复栈与受限备份保留，未发布版本。
