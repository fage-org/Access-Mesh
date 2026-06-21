# 归档批次 2026-06-21

## 归档内容

| 文档 | 原位置 | 归档原因 |
|------|--------|----------|
| [api-gap-analysis.md](api-gap-analysis.md) | `docs/plans/api-gap-analysis.md` | API 核对清单的 16 个 🔧 接口经代码核实已由 admin-service 实现（`/user/member-candidates`、`/user-role/*` 代理、`/org/{create,update,delete}`、`/user/create` 返回 `UserCreateResp` 等），与 org-user-page-impl-plan P1=100% 一致。gap 跟踪目的达成，契约权威以 [admin-service-api-contract.md](../../design/services/admin-service-api-contract.md) v1.0 为准。 |
| [org-user-page-impl-plan.md](org-user-page-impl-plan.md) | `docs/plans/org-user-page-impl-plan.md` | 「组织与用户」融合页实现计划：P0 前端骨架 + P1 后端契约（16 个 🔧 接口）+ P2 权限接线降级三阶段均 100% 完成；联动验收（user-role-proxy-fix T-ADMIN-001~019）已完成；设计回写在 T-ADMIN 任务中完成。内嵌 P0/P1/P2 任务表保留作历史记录。 |

## 当前权威入口

- 「组织与用户」页后端契约：`docs/design/services/admin-service-api-contract.md`（v1.0，2026-06-14 定稿）
- 「组织与用户」权限契约：`docs/design/org-user-permission-contract.md`（v1.2）、`docs/design/default-org-tree-user-lifecycle.md`
- 实现计划：已归档，权威依据转交上述设计文档

## 未收口项

api-gap-analysis 中 5 个 ⏳ 接口（角色管理 / 权限授予查询 / 权限变更日志 / 业务域管理 / 类型定义管理）属于 permission-center 侧，**不属于「组织与用户」页范围**，未纳入本次归档收口，待各自页面设计阶段单独跟踪。

## 历史追溯

本批次文档仅作历史核对记录，不再作为实现依据。
