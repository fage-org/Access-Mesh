# 任务看板（Task Board）

本目录是 AccessMesh 任务的**唯一权威清单**。任务为原子执行单元，归属某个 [计划](../plans/)，并声明将改动的 [设计](../design/) 章节。

> 治理规则见 skill：`.claude/skills/design-plan-task-lifecycle/SKILL.md`。任务 ID 格式 `T-<DOMAIN>-<NNN>`，各领域独立递增、ID 冻结不回收。

## 领域计数器

| 领域 | 前缀 | 下一编号 |
|---|---|---|
| permission-center | `T-PERM` | 001 |
| admin-service | `T-ADMIN` | 001 |
| gateway | `T-GW` | 001 |
| 组织/用户（跨 admin+perm） | `T-ORG` | 001 |
| 跨服务 API 契约 | `T-API` | 001 |
| 前端 | `T-FE` | 001 |

> 新建任务时从对应领域取下一编号，计数器 +1。

## 任务总表

| ID | 标题 | 计划 | 领域 | 设计引用 | 依赖 | 状态 | 回写 |

> _迁移进行中，存量任务将逐步从 `docs/plans/` 各计划抽取登记至此。_

## 依赖告警（dangling）

> 当被依赖的任务 `cancelled` 或设计被 `superseded` 时，下游任务在此登记，等待重连。

_（暂无）_

## 设计变更待核对

> 当设计文件 `status` 变为 `superseded` 或章节实质变更时，`design_refs` 指向它的任务在此登记，等待核对验收与回写目标是否仍成立。

_（暂无）_

## 已完成（done，待计划归档时清理）

_（暂无）_

---

## 字段说明

- **设计引用**：任务将改动的 `docs/design/...#章节` 锚点；任务 `done` 前必须回写这些章节。
- **依赖**：`depends_on` 的前置任务 ID；下游任务在前置 `done`/`cancelled` 前不应进 `done`。
- **状态**：`proposed` / `in-progress` / `review` / `done` / `cancelled` / `archived`
- **回写**：设计回写状态 `pending` / `done`；`done` 是任务 `done` 的前置条件。

## 新建任务流程

1. 取领域下一编号，计数器 +1，ID 冻结。
2. 总表加行。
3. 复杂任务（多步/独立决策/多验收条目）→ 开 `docs/tasks/<ID>.md` 独立文件，套用任务 frontmatter 模板（见 skill §2.3）。
4. 填 `plan` / `design_refs` / `depends_on`（防循环）/ `acceptance`。
5. 在所属 plan 的 `tasks:[]` 加该 ID，同步计划正文任务清单快照。
6. 若声明了 `depends_on`，检查无循环依赖。
