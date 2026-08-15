---
doc_type: task
id: T-ACCESS-005
title: 实现强事务权限投影并删除内部同步子系统
status: done
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#3-模块边界
  - docs/design/access-service-architecture.md#4-管理事实与权限投影
  - docs/design/permission-center/core-flows.md
  - docs/design/default-org-tree-user-lifecycle.md
  - docs/design/services/admin-service-api-contract.md#3-与-permission-center-的同步动作
  - docs/design/services/admin-service-api-contract.md#4-接口契约
  - docs/design/services/admin-service-api-contract.md#6-验收标准
  - docs/design/services/admin-service-api-contract.md#7-已确认决策-设计沉淀
depends_on:
  - T-ACCESS-002
  - T-ACCESS-004
blocks: []
acceptance:
  - "用户、组织、菜单及成员关系写入由 access.application 编排，在同一事务内维护对应权限投影"
  - "管理事实是本地实体唯一事实源；投影保留独立主键并通过稳定外部键定位"
  - "权限管理入口拒绝直接修改 access-service 所有的本地投影；外部同步所有权保持有效"
  - "删除 sys_sync_task API（Gateway 对外 /admin/sync-task/*、服务内 /sync-task/*）、实体、Mapper、builder、handler、scheduler、重试、补偿和内部 full-sync 编排；退役路径不再注册 Controller 映射"
  - "回写 admin-service-api-contract.md §3、§4 各接口的同步动作/当前差距及 §6/§7：以同事务本地权限投影取代 sys_sync_task、Feign、调度重试契约，并逐接口保留或更正明确的不同步例外"
  - "删除 access 内部 PermissionFeignClient/SyncTaskFeignClient 及相关依赖；外部 sync/full-sync 和 sync_metadata 保留"
  - "故障注入证明管理事实、权限投影和 permission_change_log 任一步失败都会整体回滚"
  - "缓存失效只在事务成功提交后发生，回滚不发布变更"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-15
---

# T-ACCESS-005 实现强事务权限投影并删除内部同步子系统

## 背景

同库后，旧 outbox/Feign 链路的最终一致性与人工补偿不再必要，应由本地事务直接保证投影一致。

## 范围

- 建立跨域写编排和投影所有权保护。
- 替换用户、组织、菜单、成员关系的内部同步调用。
- 删除完整内部同步子系统并保留外部同步能力。

## 完成记录

2026-08-15 实施完成。

- 新增 `LocalProjectionOwner` / `LocalProjectionGuard` / `LocalProjectionDomainService`：本地投影 `owner=access-service`，稳定外部键 `sys_*.id.toString()`，不写 `sync_metadata`。
- `access.application` 写编排：`UserWrite` / `OrgWrite` / `MenuWrite` / `UserOrgWrite` 标注 `@Transactional` + `@PermissionChange` + `@OperationLog`，同事务写管理事实、投影与 `permission_change_log`。
- 内部 Feign 全部替换：`AdminPermissionValidatorImpl`、`OrgVisibilityServiceImpl`、`RoleProxyServiceImpl` 改本地 `PermQueryEngine` / permission AppService。`createRoleForOrg` 与针对 `ORG/POSITION` 的菜单授权拒绝。
- 权限管理与外部 sync/full-sync 拒绝内部 `sourceService` 与保留业务键，错误码 `20042`。
- 删除 `sys_sync_task` 及内部同步子系统；`access-service.sql` 表数 33；去掉 `@EnableFeignClients` 与 Feign 依赖。
- 故障注入：`UserWriteAppServiceFaultInjectionTest` 覆盖管理事实 / 投影 / change_log 任一步失败即中止；缓存失效仍只走提交后 `@PermissionChange`。
- 设计回写：architecture §3/§4、admin-service-api-contract §3/§4/§6/§7、default-org-tree §5.3/§5.4、admin-service 同步任务模型退役说明。
- 评审补修：`revokeMenuFromRole` 走 apply-grant-plan；登录菜单改用无管理门禁的 `loadUserRolesAndPermissions`；`assignRolesBatch`/`revokeRolesBatch`/`batchCreateResources` 拒绝保留键；成员/启停/删除路径按 `abstract_user.id` / `abstract_role.id` 登记缓存失效。

**外部评审八轮修复（2026-08-15，按主题记录当前结论；用户决策 3 项）**：

- **P1（登录锁定走内部编排，用户决策：同步禁用投影）**：`AuthServiceImpl.recordLoginFail` 曾直写 `batchUpdateStatus(2)`（绕过投影/日志/失效，锁定用户已登录会话权限持续有效）。新增 `UserWriteAppService.lockUser`（无权限门禁——匿名登录路径可调用；`@Transactional`+`@PermissionChange`+`@OperationLog`），同事务更新 `sys_user.status=2` + `disableAdminUser` 投影 + change_log（operatorId=null，changeReason=login-lock）+ markUsers；管理员启用（status=1）经 `updateStatus` 自动恢复投影。补 `UserWriteAppServiceLockTest`（3 用例）。
- **P1（可选字段部分更新）**：User/Org/Menu 更新仅写提供的字段（null 跳过，内存对象保留旧值 → 投影与事实一致，不再出现 null 名称/强制 visible=false 的投影偏差）；修复 OrgUpdateReq 省略 `code` 的 NPE；`OrgUpdateReq`/`OrgCreateReq` 移除 `sys_org` 实体不支持的 `phone/email` 字段（声明必须生效）。补 `OrgWriteAppServiceTest`（8）/`MenuWriteAppServiceTest`（5）部分更新与省略字段用例。
- **P1（组织移动安全门禁）**：`updateOrg` 父级变更新增：新父级存在性校验（不存在 → ORG_NOT_FOUND）、新父级 `ADMIN_ORG:UPDATE` 门禁（防移动到无权管理节点下）、循环检测（移动到自身/子孙 → 新错误码 `ORG_PARENT_CYCLE` 10108）、`Long` 引用比较改 `Objects.equals`、自身 level 更新 + 子树 level 批量同步（`OrgDomainService.batchUpdateLevel`，单条 SQL 增量）。
- **P1（菜单转按钮投影清理）**：`updateMenu` 非按钮 → 按钮时删除旧 `ADMIN_MENU` 投影（原实现直接返回导致旧授权残留），含 change_log DELETE。
- **P1（故障注入真实事务验证）**：新增 `UserWriteAppServiceFaultInjectionIT`（`@SpringBootTest` + Testcontainers PG/Redis + `@SpyBean` 注入投影/审计故障），真实事务代理 + 真实落库断言：管理事实、投影、`permission_change_log` 任一步失败整体回滚（各表 0 残留），且回滚不发布 `PermInvalidateEvent`；成功路径提交后发布。Docker 不可用时由 Testcontainers 跳过（本机验证受限，与既有 17 个 PG 测试一致；CI/Docker 环境自动执行）。
- **P2（批量权限校验 N+1）**：`AdminPermissionValidator.checkBatchInstanceLevel` / `OrgVisibilityService.filterVisibleOrgIds` / `RoleProxyService.filterAllowedMenuIds` 改用 `engine.getDeniedIds` 一次批量（一次操作者解析 + 一次角色解析 + 批量实例级查询）；批量引擎异常从"单条静默跳过"改为整体传播（fail-closed）。删除路径 `unbindUserOrg` 循环改 `LocalProjectionDomainService.batchUnbindUserOrg`（批量加载 + 一次批量软删）。
- **P2（change_log entity_id 对齐投影主键，用户决策：BIND/UNBIND 记 user_role.id）**：`abstract_user` 相关日志 entityId 用 `abstractUserId`（DELETE/启停）；`bindUserOrg`/`unbindUserOrg` 返回 `user_role.id`，BIND/UNBIND 日志记投影主键（原混用 `sys_user.id`）；菜单删除用 `findAdminMenuResourceId`。
- **P2（/role/list 仅功能角色，用户决策：显式拒绝）**：`roleTypeCodes` 含 ORG/POSITION → `BizException` 400（原实现原样下传可暴露本地投影角色）；前端传空对象不受影响。
- **P2（文档回写）**：`default-org-tree-user-lifecycle.md` §5.5 改为"全量校准同步（仅外部业务服务）"——access-service 本地投影同事务保证，不再发起/需要 full-sync 校准；§7.1 删除已退役 `SyncFullSyncOrchestrator` 引用。
- **P3（接口/注释清理）**：`AdminRoleController` `/role/create` 标注退役接口（恒 20042 拒绝）；`AuthServiceImpl` 注释删除 Feign 描述改本地 `RoleProxyService`；`UserUpdateReq` status 注释修正（0=停用/1=正常/2=锁定）。

**验证（八轮收口）**：access-service 默认 `mvn test` **376 测试 0 失败 22 跳过**（评审基线 353 + 新增 23 单测：Lock 3/Org 8/Menu 5/Validator 4 + FaultInjectionTest 3 保持；跳过 = 17 Testcontainers + 2 历史 @Disabled + 3 故障注入 IT Docker 不可用）。新增 `UserWriteAppServiceFaultInjectionIT`（3，真实事务回滚，Docker 可用时执行）。
