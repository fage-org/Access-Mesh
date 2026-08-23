---
doc_type: task
id: T-PERM-042
title: 权限引擎显式资源 API 与实例门禁修复
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md
  - docs/design/permission-center/implementation.md
  - docs/design/access-service-architecture.md
depends_on: [T-ACCESS-016, T-ACCESS-017]
blocks: [T-ORG-001]
acceptance:
  - "引擎落地 T-ACCESS-016 定稿 API：hasPermissionByCode / getDeniedResourceCodes 对外；getDeniedEntityIds / hasPermissionByEntityId 仅引擎内部或已完成解析的调用方；泛型 <ID>、Object resourceId、toLongId() 运行时猜测全部删除；validateBatch 等抛异常便捷方法从引擎删除（引擎纯查询，异常由调用方显式抛出：admin 域经 AdminPermissionValidator 门面、permission 域 AppService if-throw）"
  - "全部 USER/ROLE 实例门禁调用点改为业务编码语义：生产代码 9 处 getDeniedIds 外部调用逐处改造（含 UserManageAppServiceImpl、RoleManageAppServiceImpl 已核实的 abstract id 错传点），改造清单在任务卡登记并以全量 grep 复核清零"
  - "USER/ROLE 等业务对象门禁调用点与跨服务 SDK 不使用 resource_entity.id，统一业务编码；资源实体管理类接口（资源树、API 映射、资源依赖、权限树——现有 ApiMappingResp/ResourceDependencyResp/ResourcePermissionTreeResp 等）保留 resource_entity.id 现状，不在本任务重构"
  - "code → entity 解析统一下沉 TypeResolutionService 批量方法（无 N+1）"
  - "授权页 3 个读接口补齐类型级 VIEW 门禁（T-ACCESS-016 §14.5 终态，随本任务落地）：abstract-role/tree→ROLE:VIEW、resource-entity/tree→RESOURCE:VIEW、operation-permission/list→OPERATION:VIEW（现状无业务门禁，bootstrap 最小种子依赖该终态；permission-condition/list 维持无门禁——api-contract §5.6 产品确认条件列表全租户开放，不在补齐范围）"
  - "USER/ROLE 实例门禁错参的正确预期测试在本任务内新增（测试自装配 resource_entity 投影 fixtures，不依赖生产写路径投影）并同提交转绿；T-ACCESS-017 特征测试保持全绿。真实 USER/ROLE 写路径产生投影后的实例授权端到端转绿归 T-ACCESS-019"
  - "单测 + PostgreSQL Testcontainers 双层验证"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-PERM-042 权限引擎显式资源 API 与实例门禁修复

## 背景

引擎当前以 `<ID> Set<ID> getDeniedIds(...)` 泛型形态接受资源 ID 并直接按 `resource_entity.id` 匹配实例授权（`PermQueryEngine` 实例路径）；生产代码 9 处外部调用中至少 6 处 USER/ROLE 门禁把 `abstract_user.id`/`abstract_role.id` 直接传入，ID 空间错位导致类型级 scopeAll 正常而实例级授权恒拒绝/误判，且三个空间同为 Long，误传可能命中另一空间合法记录。已确认方案：不做双 ID 断言与 Resolver 加固，直接按 T-ACCESS-016 定稿的显式 API 重塑整个实例权限契约。

## 范围

- `PermQueryEngine` 及门禁入口（`AdminPermissionValidatorImpl` 等）API 拆分与实现。
- 全部 USER/ROLE 实例门禁调用点改造（以全量 grep getDeniedIds 为准，含 6 处已知错传点）。
- code → entity 批量解析下沉 `TypeResolutionService`（复用规范 §8.4，禁 N+1）。

## 当前口径

- 对外语义统一业务编码（String code）；entityId 只在引擎内部流转。
- 引擎纯查询：`hasPermissionByCode`/`getDeniedResourceCodes` 对外、`getDeniedEntityIds`/`hasPermissionByEntityId` 内部；抛异常便捷方法从引擎删除，异常由调用方显式抛出（admin 域经 `AdminPermissionValidator` 门面、permission 域 AppService if-throw，见 implementation §3.1）。
- 实施顺序上先于资源类型收敛（T-ACCESS-018），本任务仍使用现行类型码，类型切换由 T-ACCESS-018 承担，两者独立提交。

## 非目标 / 遗留

- 不动 hasPermission 现有 code 解析正确的调用方语义（仅统一入口形态）。
- 不改缓存目录与 TTL；不动 scope_all/scopeMode 边界映射。
- 主体 ID 数值统一（会话/审计/user_role）不在本任务（归 T-ORG-001）。
