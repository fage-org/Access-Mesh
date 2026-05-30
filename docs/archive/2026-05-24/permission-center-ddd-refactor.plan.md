# Plan: Permission-Center DDD 重构

## Summary
按 DDD 思想重新设计 permission-center 模块的服务层与领域逻辑。核心改造：统一权限查询引擎（全部走 PermQueryEngine.query()）、拆分上帝类（ConfigManageServiceImpl → 5 个独立 AppService）、领域逻辑下沉（PermissionGrantServiceImpl → AppService + DomainService）。涉及 15 个 ServiceImpl、20 个 Controller、18 个 Mapper、18 张表。

## User Story
As a 开发者, I want permission-center 的 Service 层按 DDD 聚合划分、职责清晰、权限查询有唯一入口, So that 代码逻辑不再分散、新增功能只需关注对应聚合、权限判定结果在视图和校验中一致。

## Problem → Solution
权限查询双入口（校验走引擎、视图直查 Mapper，可能不一致）+ God Class（ConfigManageServiceImpl 管 5 个聚合）+ 16 依赖单体（PermissionGrantServiceImpl）→ 统一引擎入口 + 按聚合拆分 AppService + 领域逻辑下沉 DomainService

## Metadata
- **Complexity**: XL
- **Estimated Files**: 40+
- **Status**: 设计已确认，待实施
- **验证策略**: 渐进式。每个 Phase 内部完成 "创建→迁移消费者→迁移测试→删除旧类" 闭环，`mvn test -pl permission-center` 必须绿。

---

## 确认的设计决策（9 轮讨论 + 6 项审查修正）

### 决策 #1: 聚合设计（8 个聚合）
| 聚合 | 聚合根 | 实体 |
|------|--------|------|
| Subject | `abstract_user` | `abstract_role`, `user_role` |
| Resource | `resource_entity` | `operation_permission` |
| **Grant** ★ | `role_resource_permission` | —（独立聚合） |
| ServiceIntegration | `service_config` | `resource_api_mapping`, `resource_dependency` |
| DomainConfig | `biz_domain` | `domain_config`, `type_definition` |
| PermissionRule | `permission_conflict_rule` | `permission_condition` |
| Audit | `permission_version` | `permission_change_log`, `operation_log` |
| SystemConfig | `system_config` | — |

### 决策 #2: 统一权限查询引擎
- 引擎保持纯净：只做查询（角色解析→资源解析→操作解析→scopeAll→实例→条件→冲突→加载辅助实体）
- `forUserView` 模式仅表示"查该用户全部权限"（不指定具体 resourceCode/operationCode），引擎返回全量 PermResult
- **过滤/分页/域过滤条件不放入 PermQuery**。新建独立对象 `PermViewFilter`，AppService 拿到引擎结果后自行过滤/分页
- 新建 `PermViewResult`（不含 PermResult 污染），由 `PermViewAssembler` 从 PermResult + PermViewFilter 组装
- 所有权限判定必须经过 `PermQueryEngine.query()`

### 决策 #3: ConfigManageServiceImpl 拆分
- TypeDefinitionAppService, BizDomainAppService, DomainConfigAppService, ServiceConfigAppService, SystemConfigAppService
- ServiceInterfaceSyncService → 独立为 ServiceSyncAppService
- ConfigManageController → TypeDefinitionController（改名）

### 决策 #4: PermissionGrantServiceImpl 拆分
- 方案C：canGrant 核心逻辑下沉到 PermissionGrantDomainService
- **彻底删除 AuthorizationService**，所有 canGrant 调用迁移到 PermissionGrantDomainService
- listPermissions/listChildren 保留在 PermissionGrantAppService（配套查询）
- RolePermissionDomainService 合并到 PermissionGrantDomainService
- auto-grant 标记为独立 TODO，不在本次重构范围（旧代码中也从未实现）

### 决策 #5: PermissionServiceImpl + PermissionViewServiceImpl 合并
- **PermissionCheckAppService**: check, batchCheck, checkInterface
- **PermissionQueryAppService**: queryResources, queryScopes, queryPermissionTree, interfaceSnapshot
- **PermissionViewAppService**:
  - 用户视角（走 `engine.query(forUserView)` → 适配器组装）：`getEffectivePermissions(targetType=USER)`, `listEffectiveRoles`, `getUserResourceTree`
  - 管理查询（`engine.hasPermission()` 门禁 + 直查 Mapper）：`getEffectivePermissions(targetType=ROLE)`, `getResourcePermissions`（查某个资源被哪些角色授予）, `getRolePermissions`（查某个角色有哪些权限）
  - 视图编排：`explain`（校验 + 来源角色 + 近期变更）。`explain` 直接调 `engine.query()` 做权限校验，通过 `AuditDomainService.queryRecentChanges()` 获取近期变更（走 DomainService，避免与 `LogQueryAppService` 同层横调）
- recentChanges → LogQueryAppService
- queryPermissionTree / interfaceSnapshot 全部改为走引擎
- 删除 6 阶段流水线和 PermissionQueryContext

### 决策 #6: DomainService 层（10 个）
| DomainService | 来源 |
|--------------|------|
| `PermQueryEngine` | 增强（保留） |
| `SubjectDomainService` | 合并 UserRoleDomain + AbstractUserDomain + AbstractRoleDomain |
| `ResourceDomainService` | 重命名 ResourceEntityDomainService |
| `PermissionGrantDomainService` | 新建 + 合并 RolePermissionDomainService + AuthorizationService 逻辑 |
| `PermissionVersionDomainService` | 保留（版本号递增、缓存键构建、批量版本查询） |
| `PermissionConditionDomainService` | 保留 |
| `PermissionConflictDomainService` | 保留 |
| `DomainClassifyService` | 保留 |
| `TypeResolutionService` | 保留 |
| `AuditDomainService` | 合并 PermissionChangeDomain + OperationLogDomain（仅内部动态日志） |

删除：OperationPermissionDomainService、ResourceApiMappingDomainService、RolePermissionDomainService、AuthorizationService

RolePermEntryMapper → 移到 util 包

**DomainService 变更的消费者迁移清单**（经代码验证）：

| 操作 | 旧类 | 实际消费者 | 迁移动作 |
|------|------|-----------|---------|
| 合并 | `AbstractUserDomainService` | `UserManageServiceImpl` | → `SubjectDomainService` |
| 合并 | `AbstractRoleDomainService` | `RoleManageServiceImpl`, `PermissionGrantServiceImpl` | → `SubjectDomainService`（GroupRoleManageServiceImpl 直查 Mapper，不受影响） |
| 合并 | `UserRoleDomainService` | `PermissionGrantServiceImpl`, `PermissionServiceImpl`, `PermissionViewServiceImpl`, `PermQueryEngine`, `AuthorizationServiceImpl`, `GroupRoleManageServiceImpl`, `UserManageServiceImpl` | → `SubjectDomainService` |
| 删除 | `ResourceApiMappingDomainService` | `ResourceManageServiceImpl`（addApiMapping, removeApiMappingsByIds, listApiMappings, updateApiMapping） | → Mapper 直调 |
| 删除 | `OperationPermissionDomainService` | `PermissionGrantServiceImpl`（selectValidById） | → Mapper 直调 |
| 合并 | `RolePermissionDomainService` | `PermissionGrantServiceImpl`（revokePermissions, selectValidById） | → `PermissionGrantDomainService` |
| 合并 | `PermissionChangeDomainService` | `PermissionGrantServiceImpl`（addChildren changeLog）, `RoleManageServiceImpl`, `UserManageServiceImpl` | → `AuditDomainService` |
| 合并 | `OperationLogDomainService` | 入口级：`PermissionGrantServiceImpl`, `RoleManageServiceImpl`, `UserManageServiceImpl` 等 AppService → AOP；内部动态：`PermissionConflictDomainServiceImpl`（冲突通知 line 230）, `ServiceInterfaceSyncServiceImpl`（同步计数 line 181）→ `AuditDomainService` | 入口级 → `@OperationLog` AOP；内部动态 → `AuditDomainService.recordConflictAsync()` / `recordSyncResult()` |
| 删除 | `AuthorizationService` | 真实调用：`PermissionGrantServiceImpl`（canGrant 校验）；遗留注入（零调用）：`OperationManageServiceImpl`, `ResourceManageServiceImpl`, `RoleManageServiceImpl`, `UserManageServiceImpl`, `ServiceInterfaceSyncServiceImpl`；`ConfigManageServiceImpl` 随 Phase 2 拆分自行消除 | 真实 canGrant 逻辑 → `PermissionGrantDomainService.checkCanGrant()`；遗留注入在 Phase 3.3a 统一移除（`ServiceInterfaceSyncServiceImpl` 随 Phase 2.6 提升自然消除） |

### 决策 #7: Controller 层
- ConfigManageController → TypeDefinitionController（改名）
- 去 Manage 后缀：ConditionManageController→ConditionController 等
- **ResourceApiMappingController 保持独立路径**，仅负责 API 映射 CRUD（`/create`, `/update`, `/remove`, `/list`）
- **ServiceSyncAppService 挂在 ServiceConfigController 下**：`POST /api/perm/service-config/sync` 方法内部改为调 ServiceSyncAppService
- 其余 Controller 调用改为对应 AppService

### 决策 #8: 新编码规范（7 条）
1. 分层职责：Controller(参数适配)→AppService(编排/事务/门禁)→DomainService(领域规则)→Mapper(数据)
2. 权限查询铁律：所有判定走 engine.query()，仅管理查询/日志可直查 Mapper
3. 命名：AppService/AppServiceImpl, DomainService/DomainServiceImpl, Controller, Engine
4. 依赖：不设硬上限，逻辑内聚优先
5. 事务：AppService 声明，缓存失效 afterCommit
6. 批量加载：用 Mapper 方法，复用逻辑提升为 DomainService
7. 操作日志分类处理：
   - **入口级**（"谁、什么操作、成功/失败"）：`@OperationLog` AOP 切面统一记录
   - **内部动态日志**（含 diff 快照、异步批量写入、冲突通知）：`AuditDomainService` 显式调用

### 决策 #9: 实施顺序（6 Phase）
```
Phase 1: 底层增强 → Phase 2: ConfigManage 闭环拆分 → Phase 3: DomainService 调整+消费者迁移
→ Phase 4: 核心合并闭环 → Phase 5: 重命名+AOP+收尾 → Phase 6: 更新设计文档
```
**每 Phase 后 `mvn test -pl permission-center` 必须绿**。每个 Phase 内部完成 "创建→迁移消费者→迁移测试→删除旧类" 的闭环。

---

## 实施任务清单

### Phase 1: 底层增强（不改 API，现有测试保持绿）
| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1.1 | PermQuery 新增 forUserView 工厂方法 | `PermQuery.java` UPDATE | `forUserView(tenantId, userId)` — 纯查询信号，不带过滤参数 |
| 1.2 | 新建 PermViewFilter | `PermViewFilter.java` CREATE | 独立对象：resourceTypes, operationCodes, resourceKeyword, domainCode, excludeApiResources, includeScopePermissions, includeSourceRoles, sourceRoleLimit, pageNum, pageSize |
| 1.3 | 新建 PermViewResult | `PermViewResult.java` CREATE | 独立视图结果：含分页、来源角色、域编码映射，不污染 PermResult |
| 1.4 | 增强 PermQueryEngine.query() 支持 forUserView | `PermQueryEngine.java` UPDATE | 当 `forUserView=true` 时不指定具体 resourceCode/operationCode，查询全部角色权限并返回全量 PermResult |
| 1.5 | 新建适配器 | `PermViewAssembler.java`, `PermTreeAssembler.java`, `SnapshotAssembler.java` CREATE | 纯转换器：PermResult + PermViewFilter → PermViewResult / TreeNode / ApiPermissionEntry |
| 1.6 | 更新 PermQueryEngineTest 覆盖 forUserView | `PermQueryEngineTest.java` UPDATE | 新增 `testForUserView()` 用例 |

**验证**: `mvn test -pl permission-center` 绿。现有测试不受影响（仅新增，不修改现有代码路径）。

---

### Phase 2: ConfigManage 闭环拆分
每步按 "CREATE 新类 → 迁移 Controller 调用 → 迁移测试 → 删除旧引用" 顺序执行。

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 2.1 | TypeDefinitionAppService + TypeDefinitionController | CREATE + RENAME | 从 ConfigManageServiceImpl 提取 TypeDefinition CRUD 5 方法，ConfigManageController 直接改名 TypeDefinitionController |
| 2.2 | BizDomainAppService | CREATE | 从 ConfigManageServiceImpl 提取 BizDomain CRUD 5 方法 |
| 2.3 | 更新 BizDomainController | UPDATE | 改为调 BizDomainAppService |
| 2.4 | DomainConfigAppService | CREATE | 从 ConfigManageServiceImpl 提取 DomainConfig CRUD 4 方法 |
| 2.5 | 更新 DomainConfigController | UPDATE | 改为调 DomainConfigAppService |
| 2.6 | ServiceConfigAppService + ServiceSyncAppService | CREATE × 2 | ServiceConfig CRUD + sync；ServiceInterfaceSyncService 提升为独立 AppService。注：旧 `ServiceInterfaceSyncServiceImpl` 的 `AuthorizationService` 为零调用遗留注入，新 `ServiceSyncAppService` 不携带该依赖，自然消除 |
| 2.7 | 更新 ServiceConfigController | UPDATE | CRUD 方法改为调 ServiceConfigAppService；`POST /sync` 方法改为调 ServiceSyncAppService |
| 2.8 | SystemConfigAppService | CREATE | 从 ConfigManageServiceImpl 提取 SystemConfig CRUD 3 方法 |
| 2.9 | 更新 SystemConfigController | UPDATE | 改为调 SystemConfigAppService |
| 2.10 | 迁移测试 | UPDATE | ConfigManageServiceImplTest → 拆分为各 AppService 的单元测试 |
| 2.11 | 删除旧类 | DELETE | ConfigManageService/Impl, ConfigManageController |

**验证**: `mvn test -pl permission-center` 绿。已迁移的 5 个 Controller（TypeDefinition/BizDomain/DomainConfig/ServiceConfig/SystemConfig）调用新 AppService 通过测试。

---

### Phase 3: DomainService 层调整 + 消费者迁移
每步按 "CREATE 新服务 → 逐个迁移消费者 → 迁移测试 → DELETE 旧服务" 顺序执行。

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 3.1 | 创建 PermissionGrantDomainService | CREATE | 包含 checkCanGrant（从 AuthorizationService 下沉）、revokePermissions（从 RolePermissionDomainService 迁移。注：版本递增和缓存失效由调用方在 afterCommit 中统一负责，避免与 batchGrant 双重递增） |
| 3.2 | 迁移 PermissionGrantServiceImpl → PermissionGrantDomainService | UPDATE | batchGrant 中的 canGrant 校验改为调 PermissionGrantDomainService.checkCanGrant()；revokePermissions 调用迁移 |
| 3.3a | 清理遗留注入 | UPDATE | 移除 `OperationManageServiceImpl`, `ResourceManageServiceImpl`, `RoleManageServiceImpl`, `UserManageServiceImpl` 中 AuthorizationService 的字段和构造参数（经验证均为零调用注入。注：`ServiceInterfaceSyncServiceImpl` 的相同遗留注入已随 Phase 2.6 提升自然消除） |
| 3.3b | 迁移 PermissionGrantServiceImpl canGrant 调用 | UPDATE | batchGrant + update 中的 canGrant 校验改为调 PermissionGrantDomainService.checkCanGrant() |
| 3.4 | 删除 AuthorizationService/Impl | DELETE | 所有 canGrant 逻辑已下沉 |
| 3.5 | 创建 SubjectDomainService | CREATE | 合并 UserRoleDomainService + AbstractUserDomainService + AbstractRoleDomainService 的全部方法 |
| 3.6 | 迁移 SubjectDomainService 消费者 | UPDATE | UserManageServiceImpl, PermissionServiceImpl, PermissionViewServiceImpl, PermQueryEngine, GroupRoleManageServiceImpl, RoleManageServiceImpl, PermissionGrantServiceImpl → 改为调 SubjectDomainService（注：AuthorizationServiceImpl 已在 3.4 删除，不参与迁移） |
| 3.7 | 删除旧 DomainService | DELETE × 3 | UserRoleDomainService/Impl, AbstractUserDomainService/Impl, AbstractRoleDomainService/Impl |
| 3.8 | 创建 AuditDomainService | CREATE | 合并 PermissionChangeDomainService + OperationLogDomainService（仅内部动态日志：变更快照、异步批量写入、冲突通知）；新增 `queryRecentChanges()` / `countRecentChanges()` 方法，供 `PermissionViewAppService.explain()` 和 `LogQueryAppService.recentChanges()` 共同使用 |
| 3.9 | 迁移 AuditDomainService 消费者 | UPDATE | PermissionGrantServiceImpl（addChildren changeLog）, RoleManageServiceImpl, UserManageServiceImpl, PermissionConflictDomainServiceImpl（冲突通知 asyncRecord line 230）→ 改为调 AuditDomainService；入口级 `asyncRecord`（`ConditionManageServiceImpl`, `ConflictRuleManageServiceImpl`, `DependencyManageServiceImpl`, `OperationManageServiceImpl`, `ResourceManageServiceImpl` 等 AppService + `ServiceSyncAppService` 的同步计数 line 181）在 Phase 5.6 由 `@OperationLog` AOP 统一替换，此阶段不处理 |
| 3.10 | 删除 ResourceApiMappingDomainService | DELETE | ResourceManageServiceImpl 的 addApiMapping/removeApiMappingsByIds/listApiMappings/updateApiMapping → 改为 Mapper 直调 |
| 3.11 | 删除 OperationPermissionDomainService | DELETE | PermissionGrantServiceImpl → 改为 Mapper 直调 selectValidById |
| 3.12 | RolePermEntryMapper 移到 util 包 | MOVE | `domain/impl/RolePermEntryMapper.java` → `util/RolePermEntryMapper.java` |
| 3.13 | 迁移相关测试 | UPDATE | AuthorizationServiceImplTest → PermissionGrantDomainServiceTest；UserRoleDomainServiceImplTest → SubjectDomainServiceTest；PermissionGrantServiceImplTest（移除已删除服务的 mock）；RoleManageServiceImplTest（移除已删除服务的 mock） |

**验证**: `mvn test -pl permission-center` 绿。所有迁移后的消费者编译通过，新 DomainService 的单元测试通过。

---

### Phase 4: 核心合并闭环
每步按 "CREATE → 迁移 Controller → 迁移测试 → DELETE 旧类" 顺序执行。

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 4.1 | 创建 PermissionCheckAppService | CREATE | check, batchCheck, checkInterface（从 PermissionServiceImpl 提取） |
| 4.2 | 更新 AuthController | UPDATE | check/batchCheck/checkInterface 改为调 PermissionCheckAppService |
| 4.3 | 创建 PermissionQueryAppService | CREATE | queryResources, queryScopes, queryPermissionTree, interfaceSnapshot（全部改为走 engine.query）。需注入 `PermissionVersionDomainService`（`interfaceSnapshot` 和 `queryScopes` 依赖版本号方法） |
| 4.4 | 更新 AuthController | UPDATE | queryResources/queryScopes/interfaceSnapshot/queryPermissionTree 改为调 PermissionQueryAppService |
| 4.5 | 创建 PermissionViewAppService | CREATE | 用户视角：`getEffectivePermissions(targetType=USER)`, `listEffectiveRoles`, `getUserResourceTree` → `engine.query(forUserView)` → 适配器；管理查询：`getEffectivePermissions(targetType=ROLE)`, `getResourcePermissions`, `getRolePermissions` → `engine.hasPermission()` 门禁 + Mapper 直查/分页；视图编排：`explain` → `engine.query()` 做校验 + `AuditDomainService.queryRecentChanges()` 获取近期变更 |
| 4.6 | 更新 PermissionViewController | UPDATE | 逐端点路由：`/explain`→PermissionViewAppService；`/effective-permissions`→PermissionViewAppService；`/resource-users`→PermissionViewAppService；`/role-permissions`→PermissionViewAppService；`/effective-roles`→PermissionViewAppService；`/resource-tree`→PermissionViewAppService；`/recent-changes`→LogQueryAppService |
| 4.7 | 迁移测试 | UPDATE | PermissionServiceImplCheckInterfaceTest → PermissionCheckAppServiceTest；PermissionServiceImplQueryScopesTest + PermissionServiceImplInterfaceSnapshotTest → PermissionQueryAppServiceTest；PermissionViewServiceImpl 新增视图回归测试 |
| 4.8 | 删除旧类 | DELETE | PermissionService/Impl, PermissionViewService/Impl, PermissionQueryContext |

**验证**: `mvn test -pl permission-center` 绿。AuthController 和 PermissionViewController 全部 API 通过测试。视图输出与重构前语义一致。

---

### Phase 5: 重命名 + AOP + 收尾

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 5.1 | PermissionGrantServiceImpl → PermissionGrantAppService | RENAME | 接口 + 实现类 |
| 5.2 | 其余 Service → AppService 重命名 | RENAME × 10 | ConditionManageService, ConflictRuleManageService, DependencyManageService, GroupRoleManageService, LogQueryService, OperationManageService, PermissionVersionService, ResourceManageService, RoleManageService, UserManageService → AppService 后缀（AuthorizationService 已在 Phase 3 删除，PermissionGrantService 已在 5.1 重命名） |
| 5.3 | 更新所有 Controller 的 import | UPDATE | 改为引用新 AppService 名称 |
| 5.4 | Controller 去 Manage 后缀 | RENAME | ConditionManageController→ConditionController, OperationManageController→OperationController, ResourceManageController→ResourceController, RoleManageController→RoleController, UserManageController→UserController |
| 5.5 | @OperationLog AOP 切面 | CREATE | `@OperationLog(module, action)` 注解 + `OperationLogAspect` 切面。仅覆盖 AppService 写方法的入口级日志（"谁、什么操作、摘要"）。内部动态日志（diff 快照、冲突通知）仍由 AuditDomainService 显式调用。 |
| 5.6 | 各 AppService 添加 @OperationLog 注解 | UPDATE | 替换入口级 operationLogDomainService.asyncRecord() 调用 |
| 5.7 | 新编码规范文档 | REWRITE | `.claude/rules/permission-center-coding-standards.md` — 7 条新规范 |

**验证**: `mvn test -pl permission-center` 绿。AOP 集成测试覆盖增删改入口方法的日志记录。

---

### Phase 6: 更新项目设计文档

| # | 任务 | 文件 | 优先级 |
|---|------|------|--------|
| 6.1 | 核心对象更新为 8 聚合设计 | `plan/permission-center/overview.md` UPDATE | ★ 必改 |
| 6.2 | 权限查询链路更新为统一引擎入口 | `plan/permission-center/core-flows.md` UPDATE | ★ 必改 |
| 6.3 | 新 AppService/DomainService 结构 | `plan/permission-center/implementation.md` UPDATE | ★ 必改 |
| 6.4 | Service 层审阅反映重构后分层 | `plan/permission-center/service-layer-review.md` UPDATE | ★ 必改 |
| 6.5 | 如有分层描述需同步 | `plan/architecture.md` CHECK+UPDATE | 建议 |
| 6.6 | 文档索引和阅读顺序 | `plan/README.md` CHECK+UPDATE | 建议 |

---

## NOT Building
- 不修改数据库 schema（18 张表结构和字段不变）
- 不引入 Axon/EventBus 等重型 DDD 框架
- 不改动 permission-center 以外的模块
- 不引入新的外部依赖
- 不实现领域事件机制
- **不做批量权限校验性能优化**（`batchCheck` 逐项调 `engine.query` 保持现状）

## 每个 Phase 隐式验收要求
- 新创建的类必须有对应的单元测试（覆盖率 ≥ 原有水平）
- 迁移的测试必须保持通过
- 语义最易偏差的两个区域（用户权限视图改走引擎、@OperationLog AOP 替换）必须有显式回归用例

## Acceptance Criteria
- [ ] 权限查询有且仅有一个统一引擎入口（PermQueryEngine.query()），过滤条件独立为 PermViewFilter
- [ ] AuthorizationService 彻底删除，canGrant 逻辑在 PermissionGrantDomainService 中
- [ ] ConfigManageServiceImpl 拆分为 5 个独立 AppService，ConfigManageController 删除
- [ ] PermissionGrantServiceImpl 拆分为 AppService + DomainService（方案C）
- [ ] PermissionViewServiceImpl 不再自行实现 6 阶段流水线；用户视角方法走 engine.query(forUserView) → 适配器；资源/角色视角为管理查询（engine 门禁 + Mapper 直查）
- [ ] PermissionService/Impl, PermissionViewService/Impl 删除，调用链完整闭环
- [ ] queryPermissionTree / interfaceSnapshot 走引擎
- [ ] 所有 Service 统一重命名（AppService/DomainService 后缀）
- [ ] @OperationLog AOP 切面落地（仅入口级日志，内部动态日志由 AuditDomainService 负责）
- [ ] SubjectDomainService 合并 3 个旧 DomainService，所有消费者迁移完成
- [ ] AuditDomainService 合并 2 个旧 DomainService，日志分类清晰（AOP vs 显式调用）
- [ ] ResourceApiMappingDomainService、OperationPermissionDomainService 删除，消费者改为 Mapper 直调
- [ ] 新编码规范文档落地
- [ ] 设计文档更新
- [ ] 每 Phase 后 `mvn test -pl permission-center` 绿
