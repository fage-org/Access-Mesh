# 2026-05-24 文档归档

本目录保存 DDD 重构（Phase 1-6）完成后的过期文档和已完成的实施计划。

这些文件引用已删除的类（`AuthorizationService`、`ConfigManageServiceImpl`、`PermissionServiceImpl`、`PermissionViewServiceImpl`、`*ManageService` 等）或描述已完成的设计方案。实现时不要以本目录内容作为权威来源。

## 当前权威来源

| 主题 | 当前文档 |
|------|----------|
| 文档入口 | `plan/README.md` |
| 权限中心概念模型 | `plan/permission-center/overview.md` |
| 权限中心 API 契约 | `plan/permission-center/api-contract.md` |
| 权限中心流程链路 | `plan/permission-center/core-flows.md` |
| 权限中心实现设计 | `plan/permission-center/implementation.md` |
| 编码规范 | `plan/project-rules.md` + `.claude/rules/permission-center-coding-standards.md` |

## 归档文件

| 文件 | 原位置 | 归档原因 |
|------|--------|---------|
| `PermQueryEngine-analysis-plan.md` | `plan/` | PermQueryEngine 问题分析文档，引用已删除的 `PermissionServiceImpl`；问题已修复落地 |
| `PermQueryEngine-refactor-final.md` | `plan/` | PermQueryEngine 重构最终方案，引用已删除的 `PermissionServiceImpl`；ResolveContext 重构已完成 |
| `PermQueryEngine-phase2-design.md` | `plan/` | binaryBit 位运算 Phase 2 设计，引用已删除的 `PermissionServiceImpl`/`PermissionViewServiceImpl`；grantedBits 已落地 |
| `fine-grained-permission.md` | `plan/skills/` | 细粒度权限检查 Skill，全篇引用 `AuthorizationService`、`ConfigManageServiceImpl`（均已删除） |
| `service-permission-design.md` | `plan/permission-center/` | 服务级权限控制设计，引用 `AuthorizationService`、`ConfigManageServiceImpl`、`ResourceManageServiceImpl`（均已删除/重命名）；设计未落地 |
| `permission-center-ddd-refactor.plan.md` | `.claude/PRPs/plans/` | DDD 重构实施计划，Phase 1-6 全部完成 |
| `binary-bit-permission-model.plan.md` | `.claude/PRPs/plans/` | binaryBit 权限模型实施计划，grantedBits 已落地到代码 |
