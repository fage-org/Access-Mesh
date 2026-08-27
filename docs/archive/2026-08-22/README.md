# 归档批次 2026-08-22（T-ACCESS-012 归并收口）

## 归档原因

access-service 归并主链（T-ACCESS-001~012）全部完成。T-ACCESS-012 收口时按生命周期规则处理被取代的设计与已完成计划：旧 DDL 标记 superseded 后物理归档、原 admin-service 服务设计被 access-service-architecture 取代并归档、归并主计划 12 任务全部 done 转 completed 归档。

## 内容清单

| 文件 | 原位置 | 说明 |
|---|---|---|
| `schema/admin-service.sql` | `docs/design/schema/` | 旧 admin 库 DDL；2026-08-12（T-ACCESS-002）标 superseded，本批物理归档 |
| `schema/permission-center.sql` | `docs/design/schema/` | 旧 permission 库 DDL；同上 |
| `schema/seed-admin-operations.sql` | `docs/design/schema/` | 旧 admin 操作种子；同上 |
| `schema/seed-perm-operations.sql` | `docs/design/schema/` | 旧 perm 操作种子；同上 |
| `admin-service.md` | `docs/design/services/` | 原独立 admin-service 服务设计；`status: superseded`，被 `docs/design/access-service-architecture.md`（§2/§3/§4）取代，`/admin/**`、`/auth/**` 契约仍由 `docs/design/services/admin-service-api-contract.md`（adopted）承载 |
| `access-service-merge-plan.md` | `docs/plans/` | 归并主计划（T-ACCESS-001~012）全部 done 转 completed；后续任务 T-ACCESS-013/014 迁入 access-post-merge-plan（该计划已于 2026-08-27 完成并归档至 `docs/archive/2026-08-27/`） |

## 当前权威设计入口

- 归并后架构与边界：`docs/design/access-service-architecture.md`
- 唯一权威 DDL：`docs/design/schema/access-service.sql`
- admin 域对外契约：`docs/design/services/admin-service-api-contract.md`
- 整体拓扑：`docs/design/architecture.md`
- 后续强化计划：已完成并归档（`docs/archive/2026-08-27/access-post-merge-plan.md`；其 CI 准入前置由 T-ACCESS-017 最小 CI 落地关闭）

本批次文档仅用于历史追溯，不作为实现依据；与权威文档冲突时以权威文档为准。
