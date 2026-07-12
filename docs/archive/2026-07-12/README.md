# 归档批次 2026-07-12

## 归档内容

| 文档 | 原位置 | 归档原因 |
|------|--------|----------|
| [frontend-phase1-plan.md](frontend-phase1-plan.md) | `docs/plans/frontend-phase1-plan.md` | 前端 Phase 1 执行计划：13 页 T-FE 任务（T-FE-001~014）全部 done；API 核对清单产出（🔧❌ 项登记为 Phase 2 后端任务 T-PERM-022~034）；组件池确认（T-FE-001 done，派生 T-FE-024 归 Phase 4）；设计回写完成（13 份 `docs/design/frontend/*.md` 全 adopted）。`pnpm build`/`pnpm lint`/`pnpm typecheck` + 全量 `mvn test`（12 模块）均通过。归档条件全部达成，执行计划完成。 |

## 当前权威入口

- 前端页面设计：`docs/design/frontend/*.md`（13 份，全 adopted，Phase 3 联调仍需引用）
- 任务看板：`docs/tasks/README.md`（T-FE-001~014 全 ✅）
- 任务详情：`docs/tasks/T-FE-*.md`（各页面任务文件，含 API 核对清单 + 组件识别记录）
- 组件池权威：`docs/tasks/T-FE-001.md` + `docs/design/frontend/permission-grant.md` §15.4
- Phase 2 后端任务：`docs/plans/frontend-phase2-plan.md`（T-PERM-022~034，proposed）
- 项目路线图：`docs/plans/improvement-plan.md`（Phase 1 已标记 archived）

## 未收口项

- Phase 2 后端改造 T-PERM-022~034（13 个后端任务，对应 13 页 API 核对 🔧❌ 清单）尚未启动，归 Phase 2。
- D/E/F 重启（T-PERM-019~021）前提②（Phase 1 收尾）已满足，前提③（生产事故）不触发需 PM 授权。

## 历史追溯

本批次归档前端 Phase 1 执行计划，仅作历史记录。Phase 1 实现成果沉淀于 `frontend/` 代码 + `docs/design/frontend/` 设计文档，不再以本 plan 为实现依据。
