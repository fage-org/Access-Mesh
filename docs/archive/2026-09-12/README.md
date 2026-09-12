# 2026-09-12 归档批次：任务/计划归档制落地

## 归档原因

用户 2026-09-12 指令：任务与计划完成后无专门归档目录，完成与未完成任务/计划混在同一目录。本批次落地归档制（定案写入 design-plan-task-lifecycle skill §1.1/§3.2/§3.3/§6.5/§8）：**附属于活跃计划的单卡 done/cancelled 不归档，随计划归档；无所属计划或计划已归档的终态单卡即行归档；归档统一住 `docs/archive/YYYY-MM-DD/`，任务卡住日期目录 `tasks/` 子目录，卡 frontmatter 与看板行保留终态事实不改状态标记。**

## 本批次内容

| 内容 | 去向 |
|------|------|
| permission-query-unification-plan.md | 本目录（status 已于 2026-09-10 转 archived，四任务 T-PERM-057/T-API-003/T-PERM-058/T-PERM-059 全 done；本批补物理归档，任务卡随迁 `tasks/`） |
| 无计划归属终态单卡 18 张（T-PERM-044~051/055/056/060/061、T-ACCESS-030/031、T-ADMIN-026/027、T-GW-008、T-FE-043） | 本目录 `tasks/` |
| 2026-06-28 / 07-12 / 08-22 / 08-27 / 09-07 各历史批次任务卡（10/6/12/22/11 张） | 各日期目录 `tasks/`（补迁，含 T-ACCESS-021 证据目录 `tasks/evidence/t-access-021/`） |
| 侧挂归档 `docs/plans/archive/2026-07/` 两计划（ux-refactor/v2，2026-07-26 取消）+ 任务卡 11 张 | `docs/archive/2026-07-26/` 及其 `tasks/` |
| 侧挂归档 `docs/plans/archive/2026-06/` v3.0~v3.3 设计史 | `docs/archive/2026-06-18/` |

留守 `docs/tasks/` 的 28 张卡 = 未终态卡（T-PERM-021/035/036/054、T-ADMIN-020、T-FE-023 等无卡或占行）+ 活跃计划（frontend-phase2 / phase4 / design-audit-followup / design-review-def-followup）附属的 done/cancelled 卡。

## 归档自检执行记录

- [x] 看板（docs/tasks/README.md）94 张卡物理迁移，其中 90 张卡行链接改指 `../archive/<日期>/tasks/<ID>.md`（T-PERM-002/T-GW-004/T-GW-005/T-PERM-009 四行本为纯文本无链接）；计划链接与 `plans/archive/` 文本残留同步改写。仓库根 README.md 两处卡链接为初批扫描盲区（只扫了 docs/），已随同日双轨评审处置批次修复。
- [x] `../../tasks/T-X.md` 形式的卡链接改指同目录 `tasks/T-X.md`；permission-query-unification-plan 内链按新深度修正。
- [x] 迁移卡 frontmatter `plan` 字段改写为归档后路径（21 张：phase1×6、2026-07 两计划×11、query-unification×4）。
- [x] 全仓无 `plans/archive/` 活引用（迁移注记/历史描述文本除外）、无 `docs/tasks/evidence` 残留、无悬空卡链接（非归档区真实悬空为零，见外评全量枚举：归档区存量悬空为 2026-06-03 源码链接 92 条 + 已知豁免 4 条）。
- [x] skill 双副本（.claude/.agents）同步一致；docs/README 归档规范与归档记录、plans/README 索引行同步。

## 当前权威入口

- 任务清单唯一权威：`docs/tasks/README.md`（看板）；归档规范：`.claude/skills/design-plan-task-lifecycle/SKILL.md` §6.5
- 本批次计划对应终态设计：`docs/design/permission-center/implementation.md` §3（query-engine-unification.md 已 superseded）
