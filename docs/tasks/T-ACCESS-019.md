---
doc_type: task
id: T-ACCESS-019
title: USER/ROLE 全写路径同事务资源投影
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
  - docs/design/services/admin-service-api-contract.md
depends_on: [T-ACCESS-018]
blocks: [T-ACCESS-020, T-PERM-043]
acceptance:
  - "以全量 grep 用户/角色写路径为准（含已核实缺口：RoleManageAppServiceImpl 创建仅写 abstract_role 无投影；UserManageAppServiceImpl 用户创建路径无投影），创建/更新/启停/删除/软删全部在同事务维护 resource_entity(USER/ROLE) 投影，任一步失败整体回滚"
  - "投影一次到位：直接使用统一后主体 ID（T-ORG-001 已完成）与收敛后类型码（T-ACCESS-018 已完成）作为 resource_entity(USER/ROLE) code，无过渡键、无二次切换"
  - "复用 LocalProjectionDomainService 与 TypeResolutionService，不新增第二套同步任务、MQ 最终一致性链路或独立 Projection Manager 框架"
  - "双创建链路收敛：application 域与 permission 域管理入口的用户/角色写路径不重复实现投影（复用优先于重实现，规范 §8.4）"
  - "真实 USER/ROLE 写入后实例授权端到端测试转绿（T-PERM-042 引擎语义测试的生产写路径闭环：业务写路径产生投影 → 实例门禁按业务编码命中/拒绝正确）"
  - "缓存失效不回归：投影写路径的 evictAfterCommit/PermissionChange 标注齐全；单测 + PostgreSQL Testcontainers 验证投影与业务写同事务成功/回滚两种路径"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-24
---

# T-ACCESS-019 USER/ROLE 全写路径同事务资源投影

## 背景

核心对象资源注册不完整：菜单投影已由 T-ACCESS-015 收口（五值全量、同事务）；组织投影存在（OrgWriteAppServiceImpl）；但角色创建（RoleManageAppServiceImpl 仅写 abstract_role）与部分用户创建路径（UserManageAppServiceImpl，与 UserWriteAppServiceImpl 的已有投影并存为双链路）缺同事务 resource_entity 投影——类型级 scopeAll 可放行而实例级授权无从命中。T-PERM-042 用手工装配的投影 fixtures 验证了引擎语义，本任务让真实业务写路径产出投影，补上实例授权的写侧闭环。

本任务在主体 ID 统一（T-ORG-001）与类型收敛（T-ACCESS-018）之后实施，投影直接以最终主体 ID 与最终类型码一次写成，无过渡转换层。

## 范围

- 用户/角色全部管理写路径的投影补齐与双链路收敛。
- 启停/删除路径的投影同步维护（禁用主体/角色时投影状态与授权可用性一致）。
- 投影写入与缓存失效标注核对。

## 当前口径

- 投影与管理事实同事务（access-service 单库强事务模型，不走异步补偿）。
- code 语义：resource_entity(USER).code = 统一主体 ID、(ROLE).code = roleId（T-ACCESS-016 定稿，architecture §12.3）。
- ROLE 投影 `parent_id` 镜像角色树，父角色资源投影缺失抛 `LOCAL_PROJECTION_DEPENDENCY_MISSING` 整体回滚（fail-closed；资源投影用于权限管控，授权页角色树读 abstract_role）。
- `abstract-user/update` 门禁为实例级 `USER:MANAGE@subjectId`（与 deleteUsers、RoleManage.updateRole 对齐）。
- 投影写同事务登记 permission_change_log（change_reason=local-projection，覆盖含级联子孙的删除全量集合）。
- 管理入口（人工建资源）类型保留清单为 `{USER, ORG, MENU, ROLE}`：ROLE 资源由角色管理写路径产出（code=roleId），人工不得直建（孤儿资源 + code 撞值被投影接管）。
- 鉴权可用性口径（DDL：`abstract_user.enabled=false` 鉴权不通过；角色仅 status=1 启用）：`resolveEffectiveRoles` 将禁用主体有效角色置空；组角色仅 status=1 参与展开并剪枝非启用嵌套组子树（非 0/1 值 fail-closed 视为禁用）。
- 外部 `/api/perm/**/sync|full-sync` 三入口不产投影、无缓存失效登记（遗留，见下）；外部主体的 USER 资源按 architecture §4.3 由外部经 resource-entity sync 自行维护。

## 非目标 / 遗留

- 不改菜单/组织既有投影链路（仅核对与新类型码一致）。
- 主体 ID 统一与类型收敛分别归前置任务 T-ORG-001 / T-ACCESS-018。
- **外部 sync 三入口无投影、无缓存失效**（全量扫描发现，范围口径不纳入本任务）：`AbstractUserSyncAppServiceImpl`/`AbstractRoleSyncAppServiceImpl` 同步主体/角色不产 resource_entity 投影；`UserRoleSyncAppServiceImpl` 写 user_role 后无 `@PermissionChange`/失效登记（直接改有效角色集合却不失效 EFFECTIVE_ROLES，TTL 兜底）——待单独立项收口。
- sync 创建的外部角色作为父角色时，createRole 父投影 fail-closed 会拒绝（ROLE 父镜像的已知取舍；若 sync 投影遗留收口则自然消除）。
- **GROUP_ROLE extra 双轨不一致（存量问题，待 T-PERM-043 裁决）**：管理入口 `addGroupRoleExtraRole` 写 `user_role(target_type=GROUP_ROLE, target_id=组, relation_id=基础角色)`（仅 `extra-roles/list` 读取展示），而前向授权展开只消费角色树 + `abstract_role.extra.basicRoleIds` JSON——relation 行不参与授权展开（经 API 添加的 extra 基础角色实际不授予）；`extra.basicRoleIds` JSON 则无任何写入方（零生产者路径，反查覆盖不做）。

## 实施记录（2026-08-23）

全量写路径扫描（grep 佐证）：admin 域 UserWrite/OrgWrite/UserOrgWrite 为既有正例（事实+投影+mark+audit 同事务）；缺投影的写入口为 permission 域 `RoleManageAppServiceImpl`（create/update/move/delete 四写方法，此前全库不存在任何 resource_entity(ROLE) 写入者）与 `UserManageAppServiceImpl`（create/update/delete 三写方法）。验证：access-service 全量 674 tests 0 failures（Testcontainers IT 7 用例，真实 PostgreSQL 16 + Redis 7）。

### 1. LocalProjectionDomainService 新增四方法（复用既有 upsertResource/guard 管线）

- `upsertRoleResource(tenantId, roleId, name, status, parentRoleId)`：resource_entity(ROLE, code=roleId) upsert；parent 镜像经 `resolveParentResourceId` fail-closed；仅 status=1 映射启用（非 0/1 值 fail-closed 落禁用）。
- `softDeleteRoleResources(tenantId, roleIds)` / `softDeleteUserResources(tenantId, subjectIds)`：一次批量加载（限定 code_type=default，与 upsert 定位对称）+ owner=access-service 过滤 + 一次批量软删（外部行跳过不阻断，无 N+1）。
- `upsertUserResource(tenantId, subjectId, name, enabled)`：resource_entity(USER, code=subjectId) upsert（permission 域外部主体；LOCAL_USER 主体仍归 admin 域 upsertAdminUser 链路）。

### 2. RoleManageAppServiceImpl（四写路径）

- createRole：事实后同事务 `upsertRoleResource`（code=roleId 一次到位）+ 变更日志；新角色无授权快照/成员，不标 @PermissionChange。
- updateRole：投影镜像 name/status + `@PermissionChange` + `markRoles`（status 变化影响有效角色解析）+ 变更日志。
- moveRole：树变更**前**经 `findUserIdsByEffectiveRoles` 预计算旧父链受影响用户 markUsers（提交后旧链关系不可再发现），投影镜像新父节点，`markRoles` 覆盖新父链成员。
- deleteRoles：软删前预计算受影响用户 markUsers（提交后已删角色不可作组展开递归起点）+ `softDeleteRoleResources(allIdsToDelete)`（含级联子孙）+ `markRoleSnapshots` + 按全量删除集合登记逐角色 DELETE 变更日志。

### 3. UserManageAppServiceImpl（三写路径）

- createUser：事实后同事务 `upsertUserResource` + `@PermissionChange`/`markUsers` + 变更日志；name 可空而 resource_entity.name NOT NULL，缺省以 externalId 兜底。
- updateUser：实例级门禁（`USER:MANAGE@subjectId`）+ 投影镜像 name/enabled + `@PermissionChange`/`markUsers` + 变更日志。
- deleteUsers：`softDeleteUserResources` + 逐用户 DELETE 变更日志（一次 insertBatch）。

### 4. 鉴权可用性与失效反查口径补齐（SubjectDomainServiceImpl）

- `resolveEffectiveRoles` 批量前置 `selectDisabledIdsByIds`（AbstractUserMapper 新增 + XML）：禁用主体有效角色置空（空集回填缓存，重新启用由写路径 markUsers 失效）；单用户版经批量委托覆盖。
- 组角色展开仅 status=1 参与：`resolveGroupRolesBatch` 构建非启用集合，根组非启用展开置空；`expandInMemory` 递归遇非启用嵌套组剪枝整棵子树。
- `SubjectDomainService` 新增 `findUserIdsByEffectiveRoles`（固定 ≤4 SQL：ROLE 直绑 + GROUP_ROLE 直绑（过滤 relation 行 null 主体）+ 树祖先组 + 组用户；`invalidateRoleCacheByRoles` 重构复用同口径）。

### 5. 附带修复（存量生产 bug，IT 暴露）

- `SubjectDomainServiceImpl.selectValidRoleById` 实参 `(tenantId, roleId)` 与 mapper 约定 `(id, tenantId)` 反转——updateRole/moveRole/deleteRoles/createRole 父检查对 roleId ≠ 租户号的角色恒报「角色不存在」。全库审计 selectValidById 调用点（40+ 处），该处为唯一反转。

### 6. 管理入口保留清单增补 ROLE

`LocalProjectionOwner.isReservedResourceType` 清单换值为 `{USER, ORG, MENU, ROLE}`——resource-entity create/batch-create 人工入口拒绝直建 ROLE 资源（20045）；外部 resource-entity sync 不受影响（不走该清单）。已核实 DDL 无 resource_entity 种子、无测试/生产代码依赖人工建 ROLE 资源。

### 7. 测试

- 单测：RoleManage/UserManage AppService 投影调用断言（create/update/move/delete + updateUser 实例级命中/拒绝）；LocalProjectionGuard 保留清单含 ROLE；SubjectDomainServiceImpl 反查口径。
- `UserRoleWriteProjectionPgIT`（Testcontainers，7 用例）：① ROLE 写路径闭环——createRole 产出投影（code=roleId、owner=access-service）→ 实例 MANAGE 经生产投影命中/未授权拒绝 → updateRole 实例门禁经投影命中且 status 镜像 → deleteRoles 软删投影后编码 fail-closed 拒绝；② USER 写路径同构（code=subjectId、updateUser 实例门禁与未授权拒绝、deleteUsers 软删）；③ ROLE 父镜像——createRole 投影 parent_id 镜像、moveRole 迁移投影父节点、无投影父角色 fail-closed 整体回滚；④ 禁用主体拒鉴（enabled=false 后类型级门禁全拒 + 投影 status=0；name=null 以 externalId 兜底）；⑤ 组角色生命周期——组经生产写路径创建、BASIC 经 moveRole 挂组、成员 GROUP_ROLE 直绑预热缓存放行 → 禁用组整体失权 → 重新启用恢复 → status=2 fail-closed 失权 → 删除组级联软删两投影；⑥⑦ 投影故障注入——upsertRoleResource/upsertUserResource 抛错时 abstract_role/abstract_user 事实整体回滚、无投影/变更日志残留。

## 设计回写（2026-08-23）

- architecture §12.3：T-ACCESS-019 落地注记（管理入口范围、ROLE 父镜像 fail-closed、updateUser 实例级、sync 遗留口径）。
- architecture §4.3 / admin-service-api-contract §3 / permission-center api-contract：管理入口类型保留清单换值 `{USER, ORG, MENU, ROLE}`。
- admin-service-api-contract §3 终态口径注：USER/ROLE 投影补齐已落地。
- permission-center implementation §3.1：投影维护状态更新为已落地。
