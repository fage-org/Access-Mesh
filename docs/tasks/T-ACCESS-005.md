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

**外部评审十一轮修复（2026-08-15，4 P1 + 2 P2 全核实修复，质量建议：用户决策顺带拆分）**：

- **P1（批量写路径循环单条 UPDATE）**：新增 4 个批量 UPDATE SQL——`UserRoleMapper.batchRefreshOwner`（batchBind 已有行刷新 owner/updatedAt）、`UserRoleMapper.batchUpdateRelationByIds`（岗位迁移成员 relation）、`AbstractUserMapper.batchUpdateValues` / `ResourceEntityMapper.batchUpdateValues`（batchUpsert 已有行，每行值不同，PostgreSQL `UPDATE ... FROM (VALUES ...)` 惯用法——项目已 PG 方言，见递归 CTE 先例；测试用 Testcontainers PG）。`UserWriteAppServiceImpl.deleteUser/updateStatus` 循环 `recordChangeLog` 改为组装全部 entries 一次提交（单条 insertBatch）。`pendingInsertKeys.containsValue` O(N²) 改独立 `Set` 追踪。
- **P1（父 resource_entity 缺失 fail-open）**：`resolveParentResourceId` 与父角色同语义——父资源投影缺失抛 `LOCAL_PROJECTION_DEPENDENCY_MISSING`(20043) 整体回滚，不再静默写 `parentId=null` 脱离父树（影响 `upsertAdminOrg`/`upsertAdminMenu`）。
- **P1（岗位迁移 fail-closed 不完整）**：`migratePositionRelation` 的岗位角色投影缺失、旧所属组织角色投影缺失均抛 20043（原静默 `Set.of()`），与新所属组织缺失同语义。
- **P1（岗位拓扑约束，新错误码 `ORG_POSITION_TOPOLOGY_INVALID`(10110)）**：契约「岗位作为普通组织（orgType=1）的子节点挂入同一树，岗位自身无下级」。`createOrg` 新增 `validatePositionTopology`——岗位必须有父（非顶级）、父必须是普通组织、任何节点不能挂在岗位下；`validateOrgMove` 新增——新父是岗位则拒绝（岗位移动天然满足"父必须是组织"：顶级已由 10109 拒绝、岗位父被 10110 拒绝）。
- **P2（批量资源操作 code_type 范围）**：`batchDeleteAdminUsers`/`batchDisableAdminUsers`/`batchUpsertAdminUsers` 改 `selectByTypeAndCodesAndCodeTypes(..., Set.of("default"))`，与单条路径 `selectByTypeCodeAndCodeType` 语义一致，不再误伤外部同步其他 code_type 行。
- **P2（鉴权前暴露编码存在性）**：`createOrg` 的 `findByCode` 移到互斥门禁（类型级 CREATE / 父节点 UPDATE）与拓扑校验之后——未授权调用者无法探测编码是否存在。
- **质量建议（用户决策：顺带拆分）**：`LocalProjectionDomainServiceImpl` 835 行拆分——批量绑定/解绑/岗位迁移移至新组件 `UserRoleProjectionWriter`，批量用户投影移至 `BatchAdminUserProjectionWriter`（主类内部装配，构造签名不变，AppService 仅依赖接口，事务边界仍由 AppService 声明）；改动处注释改写为业务不变量语义。
- **回归测试**：`LocalProjectionDomainServiceImplTest` +5（岗位角色缺失/旧所属缺失/父资源缺失 fail-closed、batchUpsert 批量刷新单条 SQL、batchDelete code_type 限定）+ 既有 2 用例改断言批量方法；`OrgWriteAppServiceTest` +4（顶级岗位拒绝/岗位下创建拒绝/岗位合法创建通过/移动到岗位下拒绝）。

**验证（十一轮收口）**：access-service 默认 `mvn test` **402 测试 0 失败 22 跳过**（393 + 新增 9：LocalProjection 5 + Org 4）。

**外部评审十二轮修复（2026-08-15，2 P1 + 2 P2 全核实修复，2 项用户决策）**：

- **P1（批量 UPDATE VALUES 子句 JSONB/boolean 类型错误）**：`AbstractUserMapper.batchUpdateValues` 的 `extra`（JSONB 列）与 `enabled`（boolean 列）在 PostgreSQL 上会 42804——全 unknown 参数的 VALUES 列表被 PG 推断为 text 列，`COALESCE(text, jsonb)` 无公共类型、`text→boolean` 无赋值 cast（`stringtype=unspecified` 只救 INSERT 直接赋值，不救 VALUES 推断；`ResourceEntityMapper.batchUpdateValues` 的 `status` 同理）。修复：VALUES 每列显式 `CAST(... AS BIGINT/VARCHAR/BOOLEAN/JSONB/INT)`。新增 `LocalProjectionBatchSqlIT`（Testcontainers 真实 PG：batchUpsert 已有行路径 + JSONB extra 更新断言 + batchDelete 级联落库断言；Docker 不可用时跳过）。
- **P1（删除用户未级联软删全部 user_role，用户决策：级联软删 + 删除孤儿任务）**：`batchDeleteAdminUsers` 原只软删 `abstract_user` + ADMIN_USER 资源，功能角色 `user_role` 存活并依赖 `UserRoleOrphanCleanupTask` 延迟补偿（其 javadoc 自述为已删除的内部 envelope 机制兜底，EXT-9）。修复：新增 `UserRoleMapper.softDeleteByAbstractUserIds`（单条 SQL），`batchDeleteAdminUsers` 同一事务级联软删该用户全部 `user_role`（与 permission-center `UserManageAppServiceImpl.deleteUsers` 的级联语义对齐）；**删除 `UserRoleOrphanCleanupTask` 及 `selectOrphansByCutoff` mapper/XML、`orphan-cleanup` 配置、3 处测试 mockBean 引用**（两条删除路径均强事务覆盖，补偿调度属旧同步链路残留）。
- **P2（岗位拓扑校验未真正限定父为普通组织）**：`validatePositionTopology` 原仅排除岗位父，orgType=3 等未知类型仍可作岗位父且 `OrgCreateReq.orgType` 仅 @NotNull。修复：`createOrg` 校验 orgType ∈ {1,2}（INVALID_PARAM）；新增 `isRegularOrgType`（orgType=1/ORG/存量 null），创建与移动路径（`validateOrgMove`）的新父统一要求普通组织，未知类型拒绝。
- **P2（批量绑定/解绑 rolesByExt 缺 roleTypeCode 维度）**：`UserRoleProjectionWriter` 的角色索引原以 externalId 为键且 ORG 优先（putIfAbsent），同一 externalId 的 ORG/POSITION 双投影并存时 POSITION 请求会错误命中 ORG 角色、请求类型缺失时不会 fail-closed。修复：索引键改为 `roleTypeCode + "|" + externalId`（batchBind/batchUnbind 均按 `key.roleTypeCode()` 精确取值，与单条路径精确类型查询语义一致）。
- **质量建议（用户决策：全量清理）**：生产代码 40 处「X轮评审 P1/P2」历史注释 + 6 个文件 `sys_sync_task`/envelope/Outbox 失效注释（UserServiceImpl/MenuServiceImpl/OrgServiceImpl javadoc、UserOrgKeys 类）全部改写为业务不变量语义；无使用方的 `UserOrgKeys`（旧同步 relationKey 拼装残留）删除；`cross-service/admin-permission-sync.md` 归档至 `docs/archive/2026-08-15/`（4 个活跃设计文档导航引用同步更新；任务卡引用由 T-ACCESS-012 统一重基线，tasks/README 已登记）。
- **回归测试**：`LocalProjectionDomainServiceImplTest` +2（双投影按 roleTypeCode 精确取值 / 请求类型缺失 fail-closed）+ batchDelete 用例追加级联软删断言；`OrgWriteAppServiceTest` +3（orgType=3 拒绝 / 岗位挂未知类型父拒绝 / 移动至未知类型父拒绝）；新增 `LocalProjectionBatchSqlIT`（2 用例，Testcontainers PG）。

**验证（十二轮收口）**：access-service 默认 `mvn test` **409 测试 0 失败 24 跳过**（402 + 新增 7：LocalProjection 2 + Org 3 + BatchSqlIT 2）。

**外部评审十三轮修复（2026-08-15，1 P1 + 2 P2 + 1 P3 全核实修复，1 项用户决策）**：

- **P1（UNBIND 精确投影缺失 fail-open）**：`batchUnbindUserOrg` 原对用户/角色投影缺失 `continue` 静默跳过、单条 `unbindUserOrg` 返回 null——调用方随后删除 `sys_user_org`/`sys_org` 管理事实，错类型/残留 `user_role` 继续存活（用户删除级联无法覆盖"用户仍存在、仅成员关系被删"场景）。修复：两处均抛 `USER_ROLE_RELATION_NOT_FOUND`（与 bind 对称）整体回滚；关系不存在（用户/角色投影都在、三元组无匹配）仍为幂等 no-op。补 3 用例（批量角色缺失/批量用户缺失/单条用户缺失）。
- **P2（adopted 文档仍描述已删除同步链路，用户决策：回写为当前链路）**：`core-flows.md` §5 场景三管理端段落（sync API + 4 类 `PERM_*_SYNC` 任务）改写为「`access.application` 同一事务维护本地投影、级联清理、fail-closed，外部业务服务 sync 保留」；`org-user-permission-contract.md` 三处（`OrgSyncHandler`/Feign `/checkAuth` 调用链/`SyncTaskBuilder`+`enqueueIfPresent` 实现段）改写为本地 `PermQueryEngine`/`WriteAppService` 链路；`access-service.sql` 种子注释去除 `SyncTaskBuilder` 理由。
- **P2（归档链接失效）**：`cross-service/README.md`/`services/admin-service.md` 的 `../archive` 少一层（指向不存在的 `docs/design/archive`），修正为 `../../archive`；`docs/README.md` 目录树删除已归档文件行、历史追溯链接改指 `archive/2026-08-15/`。
- **P3（注释清理风格回归，全量收口）**：22 处 `（：`/`// ：` 残句修复为完整语义、`*/` 列首对齐、未使用 import 删除、测试 DisplayName 与行注释中全部轮次标记（八/九/十/十一/十二轮）清零（保留 T-ACCESS-003/004 带日期确认记录）；9 个纯注释文件与 4 个功能文件从十一轮提交恢复规范格式后重做后续修改（`git diff` 从 +1086/-955 收窄至 +179/-126）。
- **回归测试**：`LocalProjectionDomainServiceImplTest` +3（UNBIND 投影缺失 fail-closed 三场景）。

**验证（十三轮收口）**：access-service 默认 `mvn test` **412 测试 0 失败 24 跳过**（409 + 新增 3）。

**外部评审十四轮修复（2026-08-15，1 P1 + 3 P2 + 1 P3 全核实修复，1 项用户决策）**：

- **P1（批量 POSITION 路径仍可绕过 fail-closed）**：`UserRoleProjectionWriter.resolveRelationOrgId` 对 POSITION + `relationSysOrgId==null` 回退岗位自身 id——同 externalId 的 ORG/POSITION 双投影并存时，批量 BIND/UNBIND 命中 `ORG:<positionId>` 写入错误 relation_id（单条 `resolveRelationRoleId` 同输入直接抛错，二者语义不一致）。修复：批量预处理阶段（relationOrgExtIds 收集）对 POSITION 缺所属组织上下文抛 `LOCAL_PROJECTION_DEPENDENCY_MISSING`（20043，消息与单条一致），写入前抛错整体回滚。补 bind/unbind 双路径用例（2 个）。
- **P2（adopted 文档回写仍未收口，延续十三轮「回写为当前链路」决策）**：`api-contract.md` §6.2.2.1 规则/示例、§6.2.2.3 整节（表格 syncAction 列改用途、`PERM_*_SYNC`/`SYS_USER_ORG`/`ORG|POSITION`/`ADMIN_*` 全部改外部业务服务自有类型契约，示例改 `hr-service` + `EMP`/`TEAM_ROLE`/`HR_ORG`/`HR_MEMBER` 自有类型）、§6.2.2.5 整节重写（内部 Feign 调度认证 → 外部服务身份认证，`SyncAuthVerifier` + §6.2 安全策略矩阵引用，内部凭证链路标注 T-ACCESS-005 已删除）、scopeKey 表 `sourceType={sourceType}`、service-config/sync 示例 serviceCode 改 access-service、§3.1 可信边界 Feign 透传改凭证绑定。`org-user-permission-contract.md` 4 处（甲层 Feign `/checkAuth`→本地 `PermQueryEngine`、备注 ⁴ `OrgSyncHandler`→同事务本地投影、§8 核对 3 `OrgSyncHandlerImpl`→同事务投影、【同步闭环】剩余实现项→【本地投影闭环（T-ACCESS-005 已落地）】）。`services/admin-service.md` §组织与角色容器约束 3 处 + §与权限中心的交互整节（同步时态→本地投影时态 + 运行时查询，标注不再有跨服务同步链路）。`architecture.md` §1.4 表格 :91/:93 行级加注「已随 T-ACCESS-005 删除」（**用户决策：行级加注**，表格保持基线语义、正文留 T-ACCESS-012 统一回写）。
- **P2（新测试未证明管理事实整体回滚）**：十三轮新增用例直接调用 DomainService（无 Spring 事务代理、无管理事实 Mapper），`never().softDeleteBatch` 只证明投影软删未执行。新增 `UserOrgWriteAppServiceFaultInjectionIT`（Testcontainers+@SpyBean，与 `UserWriteAppServiceFaultInjectionIT` 同模式）：`removeUserFromOrg`/`deleteOrg` 经真实 Spring 事务路径注入 `unbindUserOrg`/`batchUnbindUserOrg` 投影缺失异常，真实断言 `sys_user_org`/`sys_org` 行仍存在（delete_flag=0）、`permission_change_log` 无新增、`user_role` 零残留、回滚不发布 `PermInvalidateEvent`；另加成功路径对照用例（投影齐全时事实删除 + change_log + 提交后发布）。Docker 不可用跳过（3 用例）。
- **P2（公共接口注释仍声明旧 fail-open 行为）**：`LocalProjectionDomainService.unbindUserOrg` javadoc「投影缺失返回 null」过时（实现已对用户/角色投影缺失抛 `USER_ROLE_RELATION_NOT_FOUND`）。改为明确幂等 no-op 与依赖缺失异常边界及错误码。
- **P3（注释与格式残留收口）**：`MenuServiceImpl` 删除描述已删除入队方法的悬空 Javadoc + 补末尾换行；`OrgWriteAppServiceImpl`/`LocalProjectionDomainService` `* ：` 残句清零；测试 DisplayName `（）` 空括号 2 处、重复 Mockito stub（同一 key stub 两次）清理；扫描确认连续空行 0、文件末尾多余换行 0、孤立 GBK 字节 0（存量文件缺末尾换行为历史问题，非本轮引入，不扩大 diff）。
- **回归测试**：`LocalProjectionDomainServiceImplTest` +2（批量 BIND/UNBIND POSITION 缺 relationSysOrgId fail-closed）；新增 `UserOrgWriteAppServiceFaultInjectionIT` +3（事务路径回滚 ×2 + 成功对照 ×1，Testcontainers）。

**验证（十四轮收口）**：access-service 默认 `mvn test` **417 测试 0 失败 27 跳过**（412 + 新增 5：LocalProjection 2 + 新 IT 3，Docker 跳过 +3）。

**外部评审十五轮修复（2026-08-15，1 P1 + 1 P2 + 1 P3 全核实修复，0 项新决策）**：

- **P1（外部 user-role 同步接口实际不可用）**：`UserRoleSyncAppServiceImpl.sync/fullSync` 先 `rejectReservedUserRoleSource` 拒 SYS_USER_ORG（20042），又只允许 `SYS_USER_ORG + ORG/POSITION`——任何输入都无法成功（文档 HR_MEMBER/TEAM_ROLE 示例必被拒），测试反而固化错误预期。修复：①删除 `SOURCE_TYPE_REQUIRED` 硬编码校验（INVALID_USER_ROLE_SOURCE_OR_TYPE 响应路径删除），改为 guard 拒绝保留键：`rejectReservedUserRoleSource`（sourceType）+ `rejectReservedSubjectType`（ADMIN_USER）+ `rejectReservedRoleType`（目标角色 + relationKey 前缀类型，新增 `rejectReservedRelationType`）——保留键 20042 整体回滚；②本地所有权守卫：BIND/UNBIND 分支对 `existing` 加 `rejectIfLocalUserRole`（外部不得改写/解绑 access-service 所有权行），full-sync 差异删除前批量查 `selectValidByIds`（新增 mapper 方法+XML）过滤本地 owner 行——仅标记 UNBOUND 不软删；③`SyncKeyCodec.userRoleScopeKey` 加 sourceType 参数（scopeKey=`sourceType={...}&roleTypeCode={...}&treeRootExternalId={...}`，不同调用方成员关系类型不再 scope 冲突），fullSync oneReq 用 scope.sourceType() 替代硬编码；④外部同步写入行所有权保持 NULL（owner 由本地投影独占）。测试：UserRoleSyncAppServiceTest 3→9（外部自有类型 BIND 成功+插入行 owner null、保留 subject/role/relationKey 类型拒绝 ×3、本地 owner 行 BIND/UNBIND 拒绝 ×2）、SyncKeyCodecEquivalenceTest +1（不同 sourceType 不同 scopeKey）、FullSyncResponseContractTest 旧 NON_RETRYABLE 预期改 item 级 ROLE_TYPE_CODE_MISMATCH 契约。
- **P2（adopted 文档仍混用已删除同步链路，延续「回写为当前链路」决策）**：`default-org-tree-user-lifecycle.md` §5 标题「同步契约」→「投影契约」、§5.1~5.3（用户/组织/成员关系同步 → `access.application` 同事务本地投影，§5.3 补 fail-closed/relation 所属组织/@PermissionChange 广播语义）、§1 规则表 1 处、§8 遗留清单 `OrgSyncHandlerImpl` 引用改 `access.application` 组织写入事务；`core-flows.md` §10.1「先同步或创建为权限中心资源」→ 本地投影+运行时查询；`overview.md` 「只保存 admin-service 同步来的事实」→ 同事务本地投影（外部 sync 仅自有类型）。
- **P3（四个外部同步实现保留不可达内部 owner 分支）**：`AbstractUserSyncAppServiceImpl`/`AbstractRoleSyncAppServiceImpl`/`ResourceEntitySyncAppServiceImpl`/`UserRoleSyncAppServiceImpl` 入口均已 `rejectInternalSourceService` 拒绝 admin-service，`localProjectionOwner()` 恒返回 null、`ADMIN_SOURCE_SERVICE`/`LOCAL_PROJECTION_OWNER` 常量与 `ownerServiceCode` 参数（applyToTarget/applyToTargetWithExisting/upsertUserRoleWithExisting）均为死代码——全部删除，插入行不再 setOwnerServiceCode（外部同步保持 NULL），SyncTaskBuilder 注释改业务语义（owner 由本地投影独占）。
- **回归测试**：UserRoleSyncAppServiceTest +6、SyncKeyCodecEquivalenceTest +1、FullSyncResponseContractTest 契约更新 1；UserRoleMapper 新增 `selectValidByIds`（XML 同步）。

**验证（十五轮收口）**：access-service 默认 `mvn test` **423 测试 0 失败 27 跳过**（417 + 新增 7，删旧预期 1）。

**外部评审十六轮修复（2026-08-15，2 P1 + 2 P2 + 1 P3 全核实修复，1 项用户决策）**：

- **P1（外部同步可接管人工或其他来源的 user_role，用户决策：extra 声明 + fail-closed，上线前补配置）**：`rejectIfLocalUserRole` 只拦 `owner=access-service`，但 owner=NULL 同时表示人工维护与外部同步——BIND 会改人工关系有效期并 backfillTargetId 指向、UNBIND 会软删人工关系、full-sync 差异删除只过滤本地 owner 仍会删他人关系。修复：BIND/UNBIND 对 existing 行加归属校验（`SyncMetadataDomainService.resolveTargetId` 按当前 sourceService+scopeKey+businessKey 查 metadata，target_id 匹配才可操作；未匹配 → `NON_RETRYABLE OWNERSHIP_CONFLICT`）。full-sync 差异删除的 targetId 天然限于当前来源 scope（BIND 不再接管后不可能指向他人行），保留 selectValidByIds 本地投影过滤为纵深。
- **P1（"调用方自有类型"未真正校验——服务-类型白名单，用户决策：方案 1 extra 声明 + fail-closed）**：新增 `SyncTypeGuard`（service/sync 包）：按认证服务身份查 `service_config`（不信任 payload；服务不存在/已删除/禁用/extra 缺失损坏/syncTypes 或分类缺失/类型未声明 → `SECURITY_DENIED SERVICE_TYPE_NOT_ALLOWED`，内部日志记真实原因不返回白名单）；`extra.syncTypes` 约定 `{"subjectTypeCodes": [...], "roleTypeCodes": [...], "resourceTypeCodes": [...], "sourceTypes": [...]}`，去首尾空白、精确匹配、不做通配/继承/大小写转换；每请求一次 DB 查询（不引入缓存）。四个 Sync AppService single/full-sync 接入（构造加 SyncTypeGuard）：主体→subjectTypeCode、角色→roleTypeCode、资源→resourceTypeCode、用户角色→subject+role+sourceType（relationKey 角色类型为引用不校验）；full-sync 校验 scope + item 主体类型去重一次校验；保留键拒绝（LocalProjectionGuard）保留为独立纵深（声明了 ADMIN_USER 也拒绝）。上线顺序：先为各同步服务补 syncTypes 声明，再部署严格校验。
- **P2（full-sync 重复项唯一约束）**：`existingByTriKey` 循环前一次性加载、插入后未写回——同 businessKey 第二项版本更高会二次 INSERT 触发 `uk_user_role` 整批回滚。修复：`seenBusinessKeyHashes.add` 返回值判断，重复 → item `NON_RETRYABLE DUPLICATE_BUSINESS_KEY`（写入前拒绝）；补正向 full-sync 成功用例（单 item 全链路）+ 重复项回归（INSERT 仅一次）。
- **P2（契约回写直接矛盾）**：`default-org-tree-user-lifecycle.md` full-sync scope 示例 `sourceType=SYS_USER_ORG` → `{sourceType}`；`UserRoleSyncScope` javadoc「固定为 SYS_USER_ORG」→ 自有类型+白名单；`access-service.sql` resource_entity.owner_service_code 注释「sourceService=admin-service 经 sync 写本地 owner」→ 本地投影由 access.application 同事务写入（sync 入口已 20042 拒绝 admin-service）。
- **P3（风格回退）**：SyncKeyCodecEquivalenceTest 轮次注释改业务不变量（不同 sourceType 隔离 scopeKey 防 full-sync 互相误清理）；UserRoleMapper.java/XML 补末尾换行；UserRoleSyncAppServiceImpl 误导性注释（「in-memory upserted 写回 backfillTargetId」）改为实际语义（backfill 在 doSyncOneInternal 内完成）。
- **回归测试**：新增 `SyncTypeGuardTest` +12（服务未注册/已删/禁用/extra 缺失/损坏/syncTypes 缺失/分类缺失/已声明/未声明/无类型要求直通/单请求只查一次/trim+大小写敏感——covers 参数顺序倒置被测试当场捕获修复）；`UserRoleSyncAppServiceTest` 9→15（白名单拒绝、人工行 BIND/UNBIND 归属拒绝、当前来源拥有行更新、正向 full-sync、重复 businessKey）；6 个既有 sync 测试类构造接入 SyncTypeGuard（mock + lenient 放行，白名单语义由 SyncTypeGuardTest 单独覆盖）。

**验证（十六轮收口）**：access-service 默认 `mvn test` **441 测试 0 失败 27 跳过**（423 + 新增 18：SyncTypeGuard 12 + UserRoleSync 6）。
