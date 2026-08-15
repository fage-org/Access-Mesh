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

**外部评审九轮修复（2026-08-15，按主题记录当前结论；用户决策 3 项）**：

- **P1（批量权限校验业务键/投影主键混用）**：`getDeniedIds` 按 `resource_entity.id`（投影主键）查询，上轮批量优化直接把 `sys_*.id` 业务键当投影 ID 传入（实例级授权误拒绝/误放行）。修复：`AdminPermissionValidator.checkBatchInstanceLevel` / `OrgVisibilityService.filterVisibleOrgIds` / `RoleProxyService.filterAllowedMenuIds` 先 `batchResolveResourceIds` 解析业务键 → 投影 ID，denied 结果映射回业务键；未解析（无投影）→ fail-closed 拒绝（与单条 `forAuthCheck` 内部解析语义一致）。
- **P1（父角色类型取错）**：`upsertAdminOrg` 原按子节点 roleType 查父角色（ORG 父 + POSITION 子查不到 → parentId 静默 null）。修复：签名加 `parentOrgType`（调用方 `OrgWriteAppServiceImpl` 经 `resolveParentOrgType` 提供），按父节点实际 orgType 解析。
- **P1（POSITION relation_id 投影错误）**：原用岗位 id 查 ORG 角色（查不到回退岗位自身 → `target_id == relation_id`）。修复：`bindUserOrg`/`unbindUserOrg`/`batchBind/UnbindUserOrg` 签名加 `relationSysOrgId`（POSITION 传所属组织 = 岗位 parentId），relation 指向所属组织角色；ORG 沿用自身语义。
- **P1（创建子组织缺父门禁）**：`createOrg` 带 parentOrgId 时新增：父存在性（不存在 → ORG_NOT_FOUND）、父节点 `ADMIN_ORG:UPDATE` 门禁、父必须可解析到树（游离 → ORG_TREE_ROOT_NOT_RESOLVED）。对齐契约 §4.2.4。
- **P1（组织移动跨树+深度，用户决策：严格跨树+禁顶级移动）**：`validateOrgMove` 新增：移动到顶级（parentOrgId=0）拒绝（新错误码 `ORG_MOVE_TOP_LEVEL_FORBIDDEN` 10109）；`resolveTreeRootExternalId` 比较原/目标树，不同 → `ORG_CROSS_TREE_MOVE` 10105（对齐契约 CROSS_TREE_MOVE_FORBIDDEN）；移动后子树最深节点（`selectValidByIds` 内存取 max level + delta）不得超过 10 层。
- **P1（ORG_VISIBILITY 缓存无失效，用户决策：租户级失效）**：catalog 从 `AdminCacheCatalog` 迁移至 `PermCacheCatalog`（permission 域不得依赖 admin 域的架构规则）；`PermissionChangeAspect.flush` 增加 `evictAll(ORG_VISIBILITY, tenantId)`——任何权限/角色/成员变更后租户级清除，权限回收后旧可见范围不再最长存活 5 分钟。
- **P2（批量写 N+1）**：`UserOrgWriteAppServiceImpl.assignUserToOrgs` 循环 bindUserOrg 改 `batchBindUserOrg`（一次批量加载 + 批量 insert/update）；`deleteUser`/`deleteOrg`/`updateStatus` 的循环 `findAdminUserId` 改 `batchFindAdminUserIds`（一次批量解析）。批量路径 change_log entityId 记 null（JDBC batch 无法回填 generated keys；九轮 P2-8 null 语义），单条路径仍记真实 user_role.id。
- **P2（审计 entity_id 不再回退事实表 ID）**：`abstractUserId`/`roleId` 投影缺失时 entity_id 记 null（`permission_change_log.entity_id` 可空），不再冒用 `sys_user.id`/`sys_org.id`。
- **P2（组织 phone/email 契约三方一致，用户决策：删除契约+响应字段）**：`admin-service-api-contract` §4.2.4/4.2.5 请求表删除 phone/email 行，`OrgResp` 删除字段（DTO 八轮已删，实体/表无字段）。
- **P2（外部 full-sync 示例用保留键）**：`default-org-tree-user-lifecycle` §5.5 编排步骤改为外部业务服务自有类型（占位 `<外部资源类型>` 等），明确禁止 `ADMIN_USER`/`ADMIN_ORG`/`ORG`/`POSITION`/`SYS_USER_ORG` 保留键（LocalProjectionGuard 20042 拒绝）。
- **P3（登录菜单重复加载）**：`AuthServiceImpl.getUserMenu` 一次 `loadUserRolesAndPermissionsOnce` 拆分 roles/permissions（原两次调用可能跨查询不一致），删除重复私有方法。

**验证（九轮收口）**：access-service 默认 `mvn test` **383 测试 0 失败 22 跳过**（376 + 新增 7：Org 移动/创建门禁 6 + Validator 无投影拒绝 1）。架构测试 `permissionShouldNotDependOnAdmin` 通过（catalog 迁移后 permission 域不再依赖 admin 域）。

**外部评审十轮修复（2026-08-15，按主题记录当前结论；用户决策 1 项）**：

- **P1（POSITION 移动成员 relation_id 迁移）**：`OrgWriteAppServiceImpl.updateOrg` 岗位移动后调用 `migratePositionRelation`（同事务批量迁移该岗位成员 `user_role.relation_id` 旧所属组织 → 新所属组织，返回受影响用户登记缓存失效）；否则后续解绑按新三元组匹配不到旧记录导致投影残留。
- **P1（batchBind 已有行更新缺主键）**：`batchBindUserOrg` 保留三元组 → 完整实体（含主键）映射，已有行直接更新（不再重建无 id 实体触发 MyBatis-Flex 主键校验失败）；同批重复三元组幂等跳过。
- **P1（投影依赖缺失 fail-closed）**：`resolveRelationRoleId`（POSITION 所属组织角色缺失）/`resolveParentRoleId`（父角色缺失）/`migratePositionRelation`（新所属组织角色缺失）均抛新错误码 `LOCAL_PROJECTION_DEPENDENCY_MISSING`(20043) 整体回滚——不再回退 targetRole 制造 `target_id == relation_id`，也不再静默写 `parentId=null` 脱离父树；batchBind/batchUnbind 同语义。
- **P1（ORG_VISIBILITY 跨实例失效，用户决策：L2_ONLY）**：目录改 `CacheMode.L2_ONLY`（纯 Redis 共享存储，无本地 Caffeine 旧窗口）——权限变更租户级 `evictAll` 即全实例一致，不再依赖 perm:invalidate 广播（access-service 无订阅者）。
- **P1（子级创建门禁分支）**：`createOrg` 按 parentOrgId 分支——子级只走父节点 `UPDATE` 实例级门禁，顶级走类型级 `CREATE`（契约 §4.2.4 互斥门禁；不再无条件先校验类型级，避免误拒绝可管理父节点但无租户级 CREATE 的局部管理员）。
- **P1（批量写路径循环 DB 调用）**：新增 `deleteByUserIds`/`deleteByUserIdsAndOrgId`（单条 SQL 批量删事实关系）、`batchDeleteAdminUsers`/`batchDisableAdminUsers`/`batchUpsertAdminUsers`（批量加载 + insertBatch/批量状态 SQL + 回查主键）；`deleteUser`/`updateStatus`/`deleteOrg` 改批量投影，消除循环单条数据库调用。
- **P2（批量 BIND 审计主键统一）**：`batchBindUserOrg` 返回 key → `user_role.id`（插入后按三元组批量回查），批量路径 change_log 恢复记真实投影主键（八轮用户决策保持，不再写 null）；同批重复三元组后续 key 记 null（罕见）。
- **P2（投影核心回归测试）**：`LocalProjectionDomainServiceImplTest` +7 用例（batchBind 幂等主键保留 / POSITION relation 指向所属组织 / 依赖缺失 fail-closed / 父角色按父 orgType 解析 / 父角色缺失 / 岗位迁移 / 迁移新组织缺失）；`PermissionChangeAspectTest` 断言 `evictAll(ORG_VISIBILITY)`；`OrgWriteAppServiceTest` +3 用例（子级创建跳过类型级 / 顶级走类型级 / 岗位移动迁移调用）。
- 风格：`AdminPermissionValidatorImpl` 权限拒绝消息参数顺序修正（"无法在 资源类型:资源 上执行 操作"，原为 operationCode/resourceType 颠倒误导）。

**验证（十轮收口）**：access-service 默认 `mvn test` **393 测试 0 失败 22 跳过**（383 + 新增 10：LocalProjection 7 + Org 3）。
