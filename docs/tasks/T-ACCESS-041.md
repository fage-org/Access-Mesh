---
doc_type: task
id: T-ACCESS-041
title: 规则与技能文件重写（能力 + 引擎口径）
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§7
  - docs/design/project-rules.md（文档治理与 §8.2 边界表述）
depends_on:
  - T-ACCESS-033
  - T-ACCESS-034
  - T-ACCESS-035
  - T-ACCESS-036
  - T-ACCESS-037
  - T-ACCESS-038
  - T-ACCESS-039
  - T-ACCESS-040
blocks: []
acceptance:
  - "permission-center-coding-standards 等规则文件重写为能力 + 引擎口径（.claude/rules 单副本，经 AGENTS.md 指针表跨环境生效）；project-rules §7.1「admin IdsReq/UserRoleListReq 不换绑」（T-PERM-065）与 §8.2 边界表述的实质口径在重写中原样保住"
  - "accessmesh-patterns / permission-query-pipeline / dual-layer-cache-framework 等受影响 skills 双副本同步更新（.claude/skills ↔ .agents/skills，cp 覆盖 + diff 验证）"
  - "AGENTS.md 指针表与领域描述更新为能力口径；验收第 5 条达成"
  - "「现行结构描述」零残留以允许清单二分判定：规则/skills/AGENTS/仍 adopted 设计中无 admin 域/permission 域作为现行结构的描述；归档文档、已终态任务卡与 registry 历史行按档案排除（其域前缀与历史句——如看板已完成区的 T-ACCESS-001 档案句——不回改）；T-PERM/T-ADMIN 等任务 ID 前缀属档案索引体系，明确不作为「域叙事残留」清理对象（定案：前缀不动）"
  - "前置形态核对：AdminOperationCode / AdminCacheCatalog / AdminErrorCode 等双轨设施已随 034/039/038 消亡（本任务依赖全部收敛任务，收尾位）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

验收第 5 条：规则文件不再按 admin/permission 域描述规范。规则与技能是结构口径的放大面（跨环境经指针表生效），须在能力包落地、契约重组与设施收敛（错误码/缓存目录/操作码单册）全部完成后重写，否则按双轨设施还活着的旧口径重写会返工。

## 范围

rules（单副本）、skills（双副本同步纪律）、AGENTS.md 指针表与描述的口径重写。

## 当前口径

- 只改口径与路径，不改规范的技术实质（分层铁律、引擎调用形态等维持各自规范内容）。
- 历史任务卡与归档文档中的域前缀描述属档案事实，不回改。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不动任务 ID 前缀体系（看板计数器维持）。
