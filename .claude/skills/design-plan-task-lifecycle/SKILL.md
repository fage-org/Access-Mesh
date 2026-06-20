---
name: design-plan-task-lifecycle
description: >-
  AccessMesh 设计文档 / 计划 / 任务三层解耦与生命周期管理。
  TRIGGER when: 讨论项目问题或设计方案时需要产出/更新文档；新建或推进计划；拆分/登记/验收任务；
  设计变更后回写设计或重连任务依赖；归档计划或设计；判定一段产出该落 design/plan/task 哪一层。
  NOT for: 权限查询实现细节（改看 permission-query-pipeline）、缓存实现细节（改看 dual-layer-cache-framework）、
  仓库通用编码模式（改看 accessmesh-patterns）。
origin: project
metadata:
  project: AccessMesh
  version: "1.0.0"
---

# 设计 / 计划 / 任务 三层生命周期管理

本技能解决 AccessMesh 文档的三类耦合痛点：

1. **设计被劈成两半**——权威设计在 `docs/design/`，但探索性设计稿（如 v3.5-design、design-review）长期住在 `docs/plans/`，导致"设计契约"与"执行计划"同目录、同文件混写。
2. **计划内嵌任务**——任务以表格行嵌在计划文件里（如 A-1~A-8、M1-M13），无独立状态/验收/进度，无法跨计划聚合，更新一处得编辑整个计划。
3. **生命周期无锚点**——设计的"现有 vs 演进"无统一标记；任务完成后"回写设计"靠人记；任务间依赖不显式，设计变更时依赖无处重连。

本技能定义三层解耦模型、frontmatter 契约、状态机、依赖与回写流程，以及一次性存量迁移 runbook。

---

## 1. 三层模型

| 层 | 目录 | 职责 | 粒度 |
|---|---|---|---|
| 设计 Design | `docs/design/` | 权威契约 + 演进方向：架构、数据模型、接口签名、原则、长期决策 | 一主题一文件（或一组文件） |
| 计划 Plan | `docs/plans/` | 编排：目标/非目标/准入/归档 + **任务清单（仅引用 ID）** | 一组为同一目标协同的任务 |
| 任务 Task | `docs/tasks/` | 原子执行单元：决策/验收/进度/回写设计/依赖 | 一个可独立验收的改动 |

**铁律：**
- 设计**只住** `docs/design/`。计划与任务**禁止**在自身文件内重定义契约；只能链接设计。
- 计划文件**只持任务清单引用**（`T-XXX` ID + 标题 + 状态快照），**不含任务详情**。任务详情在 `docs/tasks/`。
- 任务是原子单位。一个任务 = 一个可验收改动，对应明确的 `design_refs`（将改动的设计章节）。
- **任务 done 前必须回写其 `design_refs` 指向的设计章节**，否则不允许标 `done`。
- 计划在其所有任务 `done`（或 `cancelled`）后才能 `completed` → 归档。

### 1.1 目录结构

```
docs/
├── design/                    # 设计（权威 + 演进，frontmatter status 区分）
│   ├── README.md              # 设计索引（含演进方向分区）
│   ├── architecture.md
│   ├── permission-center/*.md
│   ├── services/*.md
│   ├── schema/*.sql
│   └── <topic>-evolution.md   # status: evolution 的前瞻设计稿
├── plans/                     # 计划（编排层）
│   ├── README.md              # 计划索引
│   └── <plan>.md
├── tasks/                     # 任务（原子单元）
│   ├── README.md              # 看板（任务总表，唯一权威任务清单）
│   └── <T-领域-NNN>.md        # 仅复杂任务开独立文件
└── archive/YYYY-MM-DD/        # 归档（已完成/被取代的过程文档）
```

> `tasks/README.md` 看板是任务清单的**唯一权威源**。计划 frontmatter 的 `tasks:[]` 与看板必须同步——创建/改任务时同步两边。

---

## 2. Frontmatter 契约

### 2.1 设计 Design

```yaml
---
doc_type: design
title: 权限中心二层权限模型
status: draft          # draft | adopted | evolution | superseded | archived
domain: permission-center   # permission-center | admin-service | gateway | cross-service | org-user | frontend
supersedes: docs/design/permission-center/overview.md   # 仅当本文件取代旧设计时填
superseded_by: docs/design/permission-center/v3.6.md    # 仅 status:superseded 时填
last_reviewed: 2026-06-20
---
```

| status | 语义 | 是否约束实现 |
|---|---|---|
| `draft` | 探索中、讨论稿，尚未定 | 否 |
| `adopted` | 当前权威设计 | **是** |
| `evolution` | 前瞻演进方向，整文件非约束（独立于 adopted 文件） | 否 |
| `superseded` | 被新设计取代，保留追溯；交叉链接仍可解析 | 否（看 superseded_by） |
| `archived` | 历史归档，移入 `docs/archive/` | 否 |

**演进方向落位规则：**
- 小幅、紧贴现有设计的演进 → 作为 `adopted` 文件内一个明确标注 `> 演进方向（非约束）` 的章节，文件级 status 仍 `adopted`。
- 较大、独立的前瞻方案 → 独立文件 `<topic>-evolution.md`，`status: evolution`。

### 2.2 计划 Plan

```yaml
---
doc_type: plan
title: 权限缓存失效改造
status: proposed        # proposed | active | blocked | completed | archived
domain: permission-center
design_refs:
  - docs/design/permission-center/overview.md
  - docs/design/permission-center/core-flows.md
tasks:
  - T-PERM-001
  - T-PERM-002
  - T-PERM-003
acceptance: "A-1~A-7 全完成；代码无 permission_version 残留；端到端验证通过"
last_updated: 2026-06-20
---
```

Plan 正文结构（**禁止**重写契约）：
```
## 目标
## 非目标
## 准入条件
## 任务清单（引用 tasks/README 看板，此处只列 ID+标题+状态快照）
## 归档条件
## 当前进度
```

### 2.3 任务 Task

复杂任务的独立文件：

```yaml
---
doc_type: task
id: T-PERM-001
title: Gateway 缓存改快照模式
status: proposed        # proposed | in-progress | review | done | cancelled | archived
plan: docs/plans/perm-cache-invalidation.md
domain: permission-center
design_refs:            # 本任务将改动的设计章节；done 前必须回写
  - docs/design/permission-center/core-flows.md#网关鉴权快照
depends_on:             # 依赖的任务 ID（可随设计变更重连）
  - T-PERM-006
blocks: []              # 反向依赖，自动推导，不手填
acceptance:             # 验收条目，全部满足才能 done
  - "Gateway 缓存 key 改为 user → InterfaceSnapshot"
  - "本地内存匹配鉴权通过"
design_writeback:
  required: true
  status: pending       # pending | done；done 前置条件 = design_refs 已更新
last_updated: 2026-06-20
---
```

简单任务：仅在看板表占一行，不开独立文件。看板行字段：

| ID | 标题 | 计划 | 领域 | 设计引用 | 依赖 | 状态 | 回写 |

### 2.4 任务 ID 方案

- 格式：`T-<DOMAIN>-<NNN>`，全局递增，不复用、不重排。
- 领域前缀（与 design `domain` 对齐）：

| 前缀 | 领域 |
|---|---|
| `T-PERM` | permission-center |
| `T-ADMIN` | admin-service |
| `T-GW` | gateway |
| `T-ORG` | 组织/用户（跨 admin+perm） |
| `T-API` | 跨服务 API 契约 |
| `T-FE` | 前端 |

- 编号在每个领域内独立递增，由看板 `tasks/README.md` 顶部的计数器分配，分配后即冻结（任务 cancelled 后 ID 不回收）。

---

## 3. 生命周期状态机与迁移规则

### 3.1 设计

```
draft ──采纳──▶ adopted ──新设计取代──▶ superseded ──历史归档──▶ archived
                                          ▲
adopted 内含演进章节 / 独立 evolution 文件 ──采纳──▶ adopted
```

- `adopted → superseded`：新设计文件标 `status: adopted` + `supersedes: <旧>`；旧文件标 `status: superseded` + `superseded_by: <新>`，**保留原位**（交叉链接仍可解析），不立即移走。
- 设计状态变化时，触发**设计变更依赖扫描**（见 §4.2）。

### 3.2 计划

```
proposed ──准入满足──▶ active ──阻塞──▶ blocked ──解除──▶ active
                         │
                         └──所有任务 done/cancelled──▶ completed ──归档──▶ archived
```

- 计划 `completed` 的硬条件：其 `tasks:[]` 中无 `proposed/in-progress/review` 状态任务（全部 `done` 或 `cancelled`）。
- 归档：移入 `docs/archive/YYYY-MM-DD/`，更新三处索引。

### 3.3 任务

```
proposed ──▶ in-progress ──▶ review ──回写done──▶ done ──计划归档──▶ archived
                ▲                              │
                └──阻塞/重连────────────────────┤
                                               cancelled ──▶ archived
```

- `review → done` 硬条件：
  1. `acceptance` 全部勾选；
  2. `design_writeback.required=true` 时 `design_writeback.status=done`（即 `design_refs` 指向的设计章节已更新）；
  3. 无下游 `depends_on` 本任务且仍 `in-progress` 的任务存在未处理的 dangling（若本任务被依赖，需先确认下游已重连或接受阻断）。
- `cancelled`：触发**依赖重连扫描**（见 §4.1），下游任务标 dangling，由人/AI 决定重连到谁。

---

## 4. 依赖管理与设计变更扫描

### 4.1 任务依赖

- `depends_on` 声明前置任务 ID；`blocks` 反向，**自动推导**，不手填。
- **新增/改 `depends_on` 时防循环**：沿 `depends_on` 图深度遍历，若回到自身则拒绝并报错。
- **任务 `cancelled` 触发重连扫描**：
  1. 找出所有 `depends_on` 含本 ID 的下游任务；
  2. 将这些任务的 `status` 标记为 `blocked`，在看板 `## 依赖告警` 区列出"dangling 依赖：T-X 依赖已取消的 T-Y，需重连"；
  3. 由人/AI 决定重连目标（如 A 改依赖 C），更新 `depends_on`，解除 `blocked`。
- **设计变更导致任务取消**（如 B 因设计取消）：B → `cancelled` → 触发 §4.1 → A 重连到 C。整条链路在 `## 依赖告警` 区可追溯。

### 4.2 设计变更依赖扫描

当设计文件 `status` 变为 `superseded`，或某设计章节发生实质性变更时：

1. grep `docs/tasks/` 所有任务的 `design_refs`，找出指向该文件（含章节锚）的任务；
2. 在看板 `## 设计变更待核对` 区列出"任务 T-X 的 design_refs 指向已变更/被取代的设计 <file>，需核对验收与回写目标是否仍成立"；
3. 任务作者确认后：若新设计仍涵盖该任务，更新 `design_refs` 指向新文件；若任务因此失效，转 `cancelled` 并触发 §4.1。

### 4.3 看板区段

`tasks/README.md` 至少包含：

```
## 任务总表（唯一权威清单）
| ID | 标题 | 计划 | 领域 | 设计引用 | 依赖 | 状态 | 回写 |

## 依赖告警（dangling）
## 设计变更待核对
## 已完成（done，待计划归档时清理）
```

---

## 5. 判定落层决策树

讨论产出一段内容时，按顺序判定落层：

1. 是**契约/原则/数据模型/接口签名/长期架构决策**？
   - 已定 → `docs/design/` `status: adopted`
   - 探索中 → `docs/design/` `status: draft`
   - 前瞻方向 → `docs/design/` `status: evolution`（独立文件）或 adopted 文件内标注章节
2. 是**为一组任务编排目标、非目标、准入、顺序、归档**？→ `docs/plans/` 新建/更新 plan，任务清单只引用 ID
3. 是**原子可执行改动，有明确验收 + 回写设计点**？→ `docs/tasks/` 看板登记（复杂则开文件）
4. 一份产物**同时含多层**？→ **拆成多份**，分别落层，互相用链接关联。**绝不**把设计+计划+任务写进同一文件。
5. 是**核对清单/gap 清单**？→ 每条 gap 转 task；清单本身若仍有跟踪价值，作为 plan 引用这些 task。

---

## 6. 操作流程（skill 触发时执行）

### 6.1 讨论中产出文档

1. 用 §5 决策树判定每个产出落层；
2. 套用 §2 对应 frontmatter 模板；
3. 写入正确目录；跨层关系用相对链接（`docs/design/...`、`../tasks/T-XXX.md`）；
4. 同步更新对应索引（design/plans/tasks 的 README）。

### 6.2 新建任务

1. 在 `tasks/README.md` 顶部分配 `T-<DOMAIN>-<NNN>`（领域计数器 +1，ID 冻结）；
2. 看板总表加行；
3. 若任务复杂（多步/有独立决策/验收条目多）→ 开 `docs/tasks/<ID>.md` 独立文件，套任务 frontmatter；
4. 填 `plan`（所属计划）、`design_refs`（将改动的设计章节）、`depends_on`（防循环）、`acceptance`；
5. 在所属 plan 的 `tasks:[]` 加该 ID，同步计划正文任务清单快照；
6. 触发 §4 依赖/设计扫描。

### 6.3 任务推进 done（强制回写）

1. 核对 `acceptance` 全勾；
2. 对每个 `design_refs`：打开设计文件，确认章节已反映本任务实现；未反映则**先更新设计**；
3. 设 `design_writeback.status: done`，bump 设计 `last_reviewed`；
4. 若设计因此新增契约，可能需 `draft → adopted` 转换（§3.1）；
5. 设任务 `status: done`；
6. 检查所属 plan 是否满足 `completed` 条件（§3.2）。

### 6.4 设计变更

1. 新建设计文件 `status: adopted` + `supersedes`；旧文件 `status: superseded` + `superseded_by`（保留原位）；
2. 触发 §4.2 扫描，在看板 `## 设计变更待核对` 列出受影响任务；
3. 逐任务核对：重连 `design_refs` 或转 `cancelled`（转 cancelled 再触发 §4.1）。

### 6.5 归档

- 计划 `completed` → 移入 `docs/archive/YYYY-MM-DD/`，更新 `docs/README` + `design/README` + `plans/README` + `tasks/README`；
- 设计 `superseded` 长期保留后可 `archived` 移入归档；
- 归档自检清单见 `docs/plans/README.md` §"归档/迁移自检"（链接修复、grep 残留、索引刷新）。

---

## 7. 一次性存量迁移 runbook

> **迁移状态：Step 0-3 已执行（2026-06-20），Step 4-5 为渐进迁移。** 本节保留作历史记录与剩余工作的指引。
>
> 已完成：建 tasks/ 骨架 + design frontmatter（Step 0）；v3.5-design 迁入 design/（Step 1）；design-review 拆解归档（Step 2）；3 个 P0 计划 + user-role-proxy-fix 任务抽取至看板（Step 3a/3b）；3 份路线图/gap 计划补 frontmatter（Step 3c）。
>
> 渐进迁移（Step 4-5，触达时再做）：improvement-plan 痛点诊断沉淀至 design/；api-gap-analysis gap 转 T-API；org-user-page P0/P1 任务 ID 化（与 user-role-proxy-fix 去重）。这三者强行拆解会引入重复任务，故留待后续触达相关章节时按需迁移。
>
> 以下原文保留作迁移方法参考，不再逐字执行。

### 7.1 领域前缀分配（迁移用）

| 存量计划/文档 | 领域前缀 |
|---|---|
| perm-cache-invalidation-plan | `T-PERM` |
| scope-mode-migration-plan | `T-PERM` |
| gateway-fail-mode-plan | `T-GW` |
| user-role-proxy-fix-plan | `T-ADMIN` |
| org-user-page-impl-plan | `T-ADMIN` / `T-FE` |
| improvement-plan | 按任务实际领域分 |
| api-gap-analysis | `T-API` |

### 7.2 迁移步骤

**Step 0 — 建骨架**
- 新建 `docs/tasks/` + `docs/tasks/README.md`（看板模板：总表 + 依赖告警 + 设计变更待核对 + 已完成 + 领域计数器）。
- 给 `docs/design/` 现有文件补 `doc_type: design` / `status: adopted` / `domain` frontmatter（不动正文）。

**Step 1 — 迁 v3.5-design**
- `docs/plans/permission-center-v3.5-design-2026-06-18.md` → 移至 `docs/design/permission-center-v3.5-design.md`，`status: adopted`，`domain: permission-center`。
- 其 `§0.3 v3.5.1+ 留待增量` → 抽为独立 `docs/design/permission-center-v3.5.1-evolution.md`，`status: evolution`。
- 历史版本叙述精简（详档已在 `archive/2026-06/`）。
- 更新 `design/README.md` 权威来源表。

**Step 2 — 拆 design-review**
`docs/plans/design-review-2026-06-17.md`（707 行）三段拆分：
- **① 评审结论**（§1-3：信任锚点、设计意图、文档不一致清单）→ 作为勘误/补注沉淀进对应 `design/` 文档（`architecture.md` / `implementation.md` / `api-contract.md` 等的不一致项逐条修正或加注）。
- **② 工作单 A-F**（§4）→ 已有派生计划：工作单 A=perm-cache-invalidation、B=scope-mode-migration、C=gateway-fail-mode。核对这三个 plan 的 frontmatter 补全 `design_refs` + `tasks`；D/E/F 若无派生计划则新建 plan。
- **③ 原文** → 移至 `docs/archive/2026-06-17/design-review.md`，作为评审记录（保留追溯，不再作实现依据）。

**Step 3 — 逐计划抽任务**
对每个存量计划，把内嵌任务表抽为 `docs/tasks/` 条目：
- 看板登记 `T-<DOMAIN>-NNN`，复杂任务（如 M5 改业务键、M11 抽 OrgVisibilityService、A-1 Gateway 快照改造）开独立文件；
- plan frontmatter 补 `design_refs` + `tasks:[]`；plan 正文任务表改为"ID + 标题 + 状态快照"引用；
- 抽取时顺带补 `depends_on`（如 gateway-fail-mode 依赖 perm-cache 的快照模式 → `T-GW-001 depends_on T-PERM-001`）。
- `user-role-proxy-fix-plan` 已"已完成待验收"：迁移后核对每个 M/S 任务的 `design_writeback` 是否已 done，未回写的补回写或显式标注。

**Step 4 — improvement-plan 与 api-gap-analysis**
- `improvement-plan.md`（502 行）：痛点诊断结论沉淀进 `design/`；4 阶段任务拆为 tasks（按领域分前缀）；plan 保留为 roadmap 编排（可维持单一 plan 或拆多 plan）。
- `api-gap-analysis.md`：每条 gap 转 `T-API-NNN`；清单本身作为 plan 引用这些 task。

**Step 5 — 修复交叉链接 + 刷新索引**
- 全仓 grep 旧路径（`plans/permission-center-v3.5-design-...`、`plans/design-review-...`、计划内任务表旧编号 A-1/M5 等），改为新位置 + `T-XXX` ID。
- 刷新 `docs/README.md`、`docs/design/README.md`、`docs/plans/README.md`、`docs/tasks/README.md`。
- 跑 `docs/plans/README.md` §"归档/迁移自检"清单。

### 7.3 迁移提交节奏

每 Step 一个 commit，格式 `docs(refactor): DPT 迁移 Step N — <简述>`。Step 5 完成后整体 grep 验证无残留旧引用。

---

## 8. 检查清单

新建/更新任意层文档时逐项核对：

### 落层
- [ ] 已用 §5 决策树判定落层，未把设计+计划+任务写进同一文件？
- [ ] 设计在 `docs/design/`、计划在 `docs/plans/`、任务在 `docs/tasks/`？

### Frontmatter
- [ ] 设计 `doc_type: design` + 正确 `status` + `domain`？
- [ ] 计划 `tasks:[]` 与 `tasks/README` 看板同步？
- [ ] 任务有 `id` / `plan` / `design_refs` / `depends_on` / `acceptance`？

### 生命周期
- [ ] 任务 `done` 前 `design_writeback.status=done`（已回写设计）？
- [ ] 计划 `completed` 前所有任务 `done`/`cancelled`？
- [ ] `depends_on` 改动后无循环？
- [ ] 任务 `cancelled` / 设计 `superseded` 后已触发 §4 扫描并在看板登记？

### 索引与链接
- [ ] 三处 README（design/plans/tasks）已同步？
- [ ] 跨层引用用相对链接，无悬空？
- [ ] 归档时跑了 `docs/plans/README.md` 归档自检清单？

---

## 9. 与其他技能的边界

- 权限查询实现细节 → `permission-query-pipeline`
- 缓存实现细节 → `dual-layer-cache-framework`
- 仓库通用编码模式（分层/命名/提交规范）→ `accessmesh-patterns`
- 本技能**只**管 design/plan/task 三层的解耦、生命周期、依赖与索引，不涉及各层的具体技术内容。
