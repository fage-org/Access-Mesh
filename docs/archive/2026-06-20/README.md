# 2026-06-20 归档批次：用户角色代理修复计划

> 归档日期：2026-06-20
> 归档原因：M1-M13 + S1-S3 全部完成，§6 验收通过（代码级核验 + 247 tests 0 failures），设计回写完成。满足归档条件。
> 原文：[user-role-proxy-fix-plan.md](user-role-proxy-fix-plan.md)

## 完成情况

- **代码**：M1-M13（13 项主改动）+ S1-S3（3 项配套）全部实现，2026-06-15 落地。
- **验收**：§6.1 功能可用（9 项）+ §6.2 门禁 + §6.3 数据一致性 + §6.4 编译测试（247 tests 0 failures）+ §6.5 文档，全部通过（2026-06-20 代码级核验 + 测试套件）。
- **设计回写**：16 个任务 `design_writeback` 全部 ✓。
  - M2 补 `docs/design/permission-center/api-contract.md` 跨字段校验语义
  - M13 补 `docs/design/cross-service/admin-permission-sync.md` §11.1 孤儿延迟补偿
  - M5/M6/M7/M8/M9/M10/M11/M12 经核对设计文档已涵盖
- **任务**：`T-ADMIN-001~016` 全部 ✅ done（见 [../../tasks/README.md](../../tasks/README.md)）。

## 未纳入本批（DEFERRED，待单独立项）

- **EXT-7**：`PermissionCheckAppServiceImpl.batchCheck` 逐条循环（1k 默认树后代触发 1000 次 SQL）——性能项，审计 S-024 无主。
- **EXT-8**：`SyncTaskDomainServiceImpl.enqueueAll` 逐条 insert（删 100 用户产生 300+ INSERT）——性能项，审计 S-024 无主。

## 当前权威设计入口

修复涉及的设计已沉淀至：

- [../../design/services/admin-service-api-contract.md](../../design/services/admin-service-api-contract.md) §4.4 用户-角色代理、§2.2 门禁矩阵、可见性裁剪
- [../../design/org-user-permission-contract.md](../../design/org-user-permission-contract.md) v1.4 权限矩阵（ROLE:MANAGE、业务键导向）
- [../../design/permission-center/api-contract.md](../../design/permission-center/api-contract.md) domainCode 跨字段校验语义
- [../../design/cross-service/admin-permission-sync.md](../../design/cross-service/admin-permission-sync.md) §11.1 user_role 孤儿延迟补偿

本文档仅作历史追溯，不再作为实现依据。
