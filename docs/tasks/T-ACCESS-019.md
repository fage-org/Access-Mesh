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
last_updated: 2026-08-23
---

# T-ACCESS-019 USER/ROLE 全写路径同事务资源投影

## 背景

核心对象资源注册不完整：菜单投影已由 T-ACCESS-015 收口（五值全量、同事务）；组织投影存在（OrgWriteAppServiceImpl）；但角色创建（RoleManageAppServiceImpl 仅写 abstract_role）与部分用户创建路径（UserManageAppServiceImpl，与 UserWriteAppServiceImpl 的已有投影并存为双链路）缺同事务 resource_entity 投影——类型级 scopeAll 可放行而实例级授权无从命中。T-PERM-042 用手工装配的投影 fixtures 验证了引擎语义，本任务让真实业务写路径产出投影，补上实例授权的写侧闭环。

本任务在主体 ID 统一（T-ORG-001）与类型收敛（T-ACCESS-018）之后实施，投影直接以最终主体 ID 与最终类型码一次写成，无过渡转换层。

## 范围

- 用户/角色全部写路径的投影补齐与双链路收敛。
- 启停/删除路径的投影同步维护（禁用主体/角色时投影状态与授权可用性一致）。
- 投影写入与缓存失效标注核对。

## 当前口径

- 投影与管理事实同事务（access-service 单库强事务模型，不走异步补偿）。
- code 语义：resource_entity(USER).code = 统一主体 ID、(ROLE).code = roleId（T-ACCESS-016 定稿）。

## 非目标 / 遗留

- 不改菜单/组织既有投影链路（仅核对与新类型码一致）。
- 主体 ID 统一与类型收敛分别归前置任务 T-ORG-001 / T-ACCESS-018。

## 设计决策（2026-08-23 用户拍板）

1. **ROLE 投影镜像角色树（parent_id）+ fail-closed**：与 ORG/MENU 投影哲学一致，createRole/moveRole 解析父角色资源投影，缺失抛 `LOCAL_PROJECTION_DEPENDENCY_MISSING` 整体回滚。资源树用途是权限管控；授权页角色树读 abstract_role，后续可加权限校验。
2. **外部 sync 三入口不纳入**：本任务仅管理入口（RoleManage/UserManage）；外部主体/角色的 USER/ROLE 资源按 architecture §4.3 由外部经 resource-entity sync 自行维护，`UserRoleSync` 缓存失效缺口在下方遗留节登记。
3. **`abstract-user/update` 门禁升实例级**：从类型级 `USER:MANAGE`（scopeAll）改为按 `USER:{subjectId}` 业务编码实例判定，与同文件 deleteUsers、RoleManage.updateRole 对齐（权限语义变化经用户确认）。
4. **投影写补齐 permission_change_log**：与 UserWriteAppServiceImpl 正例同模式，每个投影写同事务登记 entry（change_reason=local-projection）。

## 实施记录（2026-08-23）

全量写路径扫描（grep 佐证）：admin 域 UserWrite/OrgWrite/UserOrgWrite 为既有正例（事实+投影+mark+audit 同事务）；缺投影的写入口为 permission 域 `RoleManageAppServiceImpl`（create/update/move/delete 四写方法，全库此前不存在任何 resource_entity(ROLE) 写入者）与 `UserManageAppServiceImpl`（create/update/delete 三写方法）。全部验证通过：access-service 670 tests 0 failures（含新增 Testcontainers IT 4 用例，真实 PostgreSQL 16 + Redis 7）。

### 1. LocalProjectionDomainService 新增四方法（复用既有 upsertResource/guard 管线）

- `upsertRoleResource(tenantId, roleId, name, status, parentRoleId)`：resource_entity(ROLE, code=roleId) upsert；parent 镜像经 `resolveParentResourceId` fail-closed。
- `softDeleteRoleResources(tenantId, roleIds)` / `softDeleteUserResources(tenantId, subjectIds)`：一次批量加载 + owner=access-service 过滤 + 一次批量软删（外部行跳过不阻断，无 N+1）。
- `upsertUserResource(tenantId, subjectId, name, enabled)`：resource_entity(USER, code=subjectId) upsert（permission 域外部主体；LOCAL_USER 主体仍归 admin 域 upsertAdminUser 链路）。

### 2. RoleManageAppServiceImpl（四写路径）

- createRole：事实后同事务 `upsertRoleResource`（code=roleId，一次到位无过渡键）+ 变更日志；新角色无授权快照/成员，不标 @PermissionChange。
- updateRole / moveRole：投影镜像 name/status/parent + `@PermissionChange` + `markRoles`（status 禁用/移动影响有效角色解析，afterCommit 反查受影响用户失效）+ 变更日志。
- deleteRoles：`softDeleteRoleResources(allIdsToDelete)`（含级联子孙角色）+ 在既有 markRoleSnapshots 基础上补 `markRoles`（PermissionChangeContext 既有注释明确角色删除场景二者叠加）。

### 3. UserManageAppServiceImpl（三写路径）

- createUser：事实后同事务 `upsertUserResource` + 补 `@PermissionChange`/`markUsers`（原缺失）+ 变更日志。
- updateUser：门禁升实例级（决策 3）+ 投影镜像 name/enabled + `@PermissionChange`/`markUsers`（enabled 影响授权可用性，原缺失）+ 变更日志。
- deleteUsers：`softDeleteUserResources` + 逐用户 DELETE 变更日志（一次 insertBatch，与 UserWrite 删除路径同模式）。

### 4. 测试

- 单测：RoleManage/UserManage AppService 测试补投影调用断言（create/update/delete + updateUser 实例级拒绝/命中）；OperationLogRuntimeContextAppServiceTest 构造器同步。
- 新增 `UserRoleWriteProjectionPgIT`（Testcontainers，4 用例）：① ROLE 写路径闭环——createRole 产出投影（code=roleId、owner=access-service）→ 实例 MANAGE 经生产投影命中/未授权拒绝 → updateRole 实例门禁经投影命中且 status 镜像 → deleteRoles 软删投影后编码 fail-closed 拒绝；② USER 写路径同构（code=subjectId、updateUser 实例门禁、deleteUsers 软删）；③④ 投影故障注入——upsertRoleResource/upsertUserResource 抛错时 abstract_role/abstract_user 事实整体回滚、无投影/变更日志残留。装配策略：创建者（scopeAll CREATE）与管理员（实例 MANAGE，事实/投影就绪后装配）分离，规避中间改授权的快照缓存陈旧。

### 5. 附带修复（IT 暴露的存量生产 bug）

`SubjectDomainServiceImpl.selectValidRoleById` 实参 `(tenantId, roleId)` 与 mapper 约定 `(id, tenantId)` 反转——updateRole/moveRole/deleteRoles/createRole 父检查对 roleId ≠ 租户号的角色恒报「角色不存在」。全库审计 selectValidById 调用点（40+ 处），该处为唯一反转。修复 + IT 回归绿。

## 遗留（本任务范围外，已登记）

- **外部 sync 三入口无投影、无缓存失效**（全量扫描发现，2026-08-23 用户决策不纳入）：`AbstractUserSyncAppServiceImpl`/`AbstractRoleSyncAppServiceImpl` 同步主体/角色不产 resource_entity 投影（外部主体 USER 资源按 §4.3 分工由外部 resource-entity sync 自行维护）；`UserRoleSyncAppServiceImpl` 写 user_role 后无 `@PermissionChange`/失效登记（直接改有效角色集合却不失效 EFFECTIVE_ROLES，TTL 兜底）——待单独立项收口。
- sync 创建的外部角色作为父角色时，createRole 父投影 fail-closed 会拒绝（决策 1 已知取舍；若 sync 投影遗留收口则自然消除）。

## 设计回写（2026-08-23）

- architecture §12.3：T-ACCESS-019 落地注记（管理入口范围、ROLE 父镜像 fail-closed、updateUser 实例级、sync 遗留口径）。
- admin-service-api-contract §3 终态口径注：USER/ROLE 投影补齐已落地。
- permission-center implementation §3.1：投影维护状态更新为已落地。
