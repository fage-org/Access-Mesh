# 2026-06-17 归档批次：AccessMesh 设计评审记录

> 归档日期：2026-06-20（DPT 三层解耦迁移 Step 2）
> 归档原因：评审已完成，结论已沉淀，工作单 A-C 已派生计划，D/E/F 待派生。本文件为评审过程记录，仅作历史追溯，不再作为实现依据。
> 评审原文：[design-review.md](design-review.md)

## 评审产物去向（DPT 迁移后）

评审原文同时承载了"设计结论 + 工作单编排"两层，迁移后拆解如下：

### ① 设计结论（原文 §1-3）→ 已沉淀/对照 design/

| 原文章节 | 内容 | 当前权威位置 |
|---|---|---|
| §1 设计意图备忘 | 通用权限事实源定位、双事实映射、最终一致取舍 | [../design/architecture.md](../../design/architecture.md)、[../design/permission-center/overview.md](../../design/permission-center/overview.md) |
| §2 信任锚点（D1-D10 文档不一致清单）| 文档自报数字与源码差异 | **逐条修正待办**（见下"待办"）|
| §3 待确认清单 Q&A | 8 项评审基准答复 | 已融入各工作单决策；Q1-Q8 结论见原文 §11 决策记录表 |

### ② 工作单（原文 §4 A-F）→ 计划 + 任务

| 工作单 | 状态 | 派生计划 | 任务（Step 3 抽取）|
|---|---|---|---|
| A 权限延迟生效 | P0 已重启 | [../../plans/perm-cache-invalidation-plan.md](../../plans/perm-cache-invalidation-plan.md) | `T-PERM-*` |
| B 数据权限空集 | P0 已重启 | [../../plans/scope-mode-migration-plan.md](../../plans/scope-mode-migration-plan.md) | `T-PERM-*` |
| C Gateway 失联兜底 | P0 已重启 | [../../plans/gateway-fail-mode-plan.md](../../plans/gateway-fail-mode-plan.md) | `T-GW-*`（依赖工作单 A）|
| D 防呆机制 | P1 暂缓 | （未派生，重启条件见原文 §11）| — |
| E 清理预设 | P1 暂缓 | （未派生）| — |
| F 文档+简化 | P2 暂缓 | （未派生）| — |

### ③ 原文 → 归档

[design-review.md](design-review.md) 完整保留，含 §5 路线图、§6 共性根因、§7 新增实体改动对比、§8 已知问题根因核查、§9 验收、§10 索引、§11 决策记录、§12 归档条件。仅作历史追溯。

## 待办（迁移未覆盖项）

- **D1-D10 逐条修正**：原文 §2 文档不一致清单的数字勘误需逐条核对当前源码后修正对应 design/ 文档。该项归工作单 F-1.a（文档数字自动化，P2 暂缓）负责——待 F 重启时由脚本扫描源码生成 `_metrics.md` 根治，而非手工逐条改。本次迁移**不**凭 06-17 旧评审数字去改设计文档（避免引入过期/错误数字）。
- **D/E/F 派生计划**：工作单 D/E/F 暂缓，重启条件见原文 §11。重启时新建 plan 并抽取任务至 `docs/tasks/`。

## 当前权威设计入口

- 微服务架构：[../../design/architecture.md](../../design/architecture.md)
- 权限中心设计：[../../design/permission-center/](../../design/permission-center/) + [../../design/permission-center-v3.5-design.md](../../design/permission-center-v3.5-design.md)
- 当前计划索引：[../../plans/README.md](../../plans/README.md)
- 任务看板：[../../tasks/README.md](../../tasks/README.md)
