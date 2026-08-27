---
doc_type: task
id: T-ADMIN-024
title: 恒拒绝退役 API 直接删除（含 /role/revoke-menu，共 5 个端点）
status: done
plan: docs/archive/2026-08-27/product-vertical-slice-plan.md
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/org-user-permission-contract.md
  - docs/design/access-service-architecture.md
  - docs/design/architecture.md
  - docs/design/default-org-tree-user-lifecycle.md
depends_on: [T-ACCESS-021]
blocks: [T-ACCESS-026]
acceptance:
  - "执行前 grep 复核前端/SDK/example/活文档对 5 个端点无阻塞性引用——/role/revoke-menu 与 /role/grant-menu 同 Controller、同 RoleMenuReq DTO、同 10111 恒拒绝，成对一并删除；frontend/src/api/user-manage.ts 的 assignRole/revokeRole 为 mock 阶段裸路径（真实模式不经 Gateway 路由），本次不动前端，迁移 /api/perm/user-role/* 属前端联调任务范围"
  - "直接删除恒拒绝映射及配套：/role/create、/role/grant-menu、/role/revoke-menu（AdminRoleController，含嵌套 CreateRoleReq/RoleMenuReq）、/user-role/assign、/user-role/revoke（AdminUserRoleController）与 UserRoleAssignReq/UserRoleRevokeReq DTO；ROLE_API_RETIRED(10111) 错误码退役（码值不复用，ErrorCodeContractTest 新增 RETIRED_POST_MERGE_ADMIN 退役登记断言——10111 为归并后新增码不在 PRE_MERGE_BASELINE 内，现有 RETIRED_ADMIN_NAMES 机制只覆盖基线项）"
  - "不建兼容路由层、不做别名双写；契约文档同步移除对应条目（admin-service-api-contract §2 门禁矩阵两行/§3 投影表一行/§4.4.2 §4.4.3 两小节/附录 A 两行与合计 22→19；org-user-permission-contract 六处；access-service-architecture、architecture.md、default-org-tree-user-lifecycle 各处）"
  - "负向验收：RetiredRoleApiContractTest 改写为 MockMvc standalone——5 个已删端点 POST 断言 404 无映射 + 读端点 /role/list、/user-role/list 200；HttpApiPathSnapshotTest 快照 195→190 条并在 RETIRED_PATHS 登记 5 条防回归；既有单测全绿"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-27
---

# T-ADMIN-024 恒拒绝退役 API 直接删除

## 背景

5 个退役端点保留映射但恒拒绝：/role/create（恒 20045）、/role/grant-menu、/role/revoke-menu（恒 10111，AdminRoleController）；/user-role/assign、/user-role/revoke（恒 10111，AdminUserRoleController）。已核实无任何真实调用方（前端 role-manage 已切换新路径；user-manage.ts 的 assignRole/revokeRole 为 mock 阶段裸路径，真实模式不经 Gateway 路由；SDK/示例均只用 /api/perm/* 新路径，旧路径引用仅存归档文档）。按计划「没有存量调用方，不创建兼容层，直接删除旧入口」约束处置。

## 范围

- 删除 5 个映射、配套 DTO（UserRoleAssignReq/UserRoleRevokeReq/CreateRoleReq/RoleMenuReq）与退役错误码 10111；契约文档同步。

## 当前口径

- 直删不保留兼容层；错误码码值不复用（ErrorCodeContractTest RETIRED_POST_MERGE_ADMIN 登记断言：枚举名不残留、码值不被复用）。
- admin 侧仅保留读聚合：/role/list、/role/my-info、/user-role/list（跨域只读查询服务）。
- 已删端点语义：access-service 无任何 Handler 映射（带身份直连观察值 404；匿名直连 admin 路径族先被 RequestContextInterceptor 拒为 401；经 Gateway 的未注册路径先被接口快照按 unregistered-policy=DENY 拦为 403，fail-closed）。

## 非目标 / 遗留

- 不扫描/清理归档文档中的历史引用（archive 不作为实现依据）。
- 前端 user-manage.ts assignRole/revokeRole 迁移新路径：待前端联调任务（真实模式下原裸路径本就不路由，无功能影响）。

## 验证

- RetiredRoleApiContractTest 6/6（5×404 + 读端点 200）、HttpApiPathSnapshotTest 7/7（190 条）、ErrorCodeContractTest 7/7（含退役登记）。
- 全量回归见提交记录。
