# Service 层复用性审查（Phase 1-5 完成总结）

## 审查完成

Phase 1-5 已完成，审查目标已达成。下面记录当前（重构后）的架构状态。

---

## 当前架构（重构后）

### 调度层 AppService（20个）

| AppService                  | 主要职责                                                    | 说明                                                                       |
| --------------------------- | ----------------------------------------------------------- | -------------------------------------------------------------------------- |
| PermissionCheckAppService   | 权限校验（check/batch-check/check-interface）               | 替代旧 PermissionServiceImpl                                               |
| PermissionQueryAppService   | 权限查询（query-resources/query-scopes/interface-snapshot） | 与校验分离                                                                 |
| PermissionViewAppService    | 权限视图（effective-roles/permissions/explain）             | 替代旧 PermissionViewServiceImpl；recent-changes 已迁至 LogQueryAppService |
| PermissionGrantAppService   | 权限授予/撤销（save/revoke/children）                       | 替代旧 PermissionGrantServiceImpl                                          |
| ResourceManageAppService    | 资源管理（service/resource-entity/resource-dependency）     | 替代旧 ResourceManageServiceImpl                                           |
| RoleManageAppService        | 角色管理（abstract-role/user-role）                         | 替代旧 RoleManageServiceImpl                                               |
| UserManageAppService        | 用户管理（abstract-user）                                   | 替代旧 UserManageServiceImpl                                               |
| TypeDefinitionAppService    | 类型定义管理                                                | 替代旧 ConfigManageServiceImpl（部分）                                     |
| BizDomainAppService         | 业务域管理                                                  | 替代旧 ConfigManageServiceImpl（部分）                                     |
| DomainConfigAppService      | 域配置管理                                                  | 替代旧 ConfigManageServiceImpl（部分）                                     |
| ServiceConfigAppService     | 服务配置管理                                                | 替代旧 ConfigManageServiceImpl（部分）                                     |
| ServiceSyncAppService       | 服务接口同步                                                | 新拆分                                                                     |
| SystemConfigAppService      | 系统配置管理                                                | 替代旧 ConfigManageServiceImpl（部分）                                     |
| ConditionAppService         | 权限条件管理                                                | 替代旧 AdvancedFeatureServiceImpl（部分）                                  |
| ConflictRuleAppService      | 冲突规则管理                                                | 替代旧 AdvancedFeatureServiceImpl（部分）                                  |
| DependencyAppService        | 资源依赖管理                                                | 替代旧 AdvancedFeatureServiceImpl（部分）                                  |
| GroupRoleAppService         | 分组角色管理                                                | 新拆分                                                                     |
| OperationAppService         | 操作权限管理                                                | 替代旧 OperationManageServiceImpl                                          |
| PermissionVersionAppService | 版本管理                                                    | 替代旧 PermissionVersionServiceImpl                                        |
| LogQueryAppService          | 日志查询                                                    | 合并旧 PermissionChangeLogServiceImpl + OperationLogQueryServiceImpl       |

### 领域层 DomainService（12个）

| DomainService                    | 主要职责         | 说明                                                                                         |
| -------------------------------- | ---------------- | -------------------------------------------------------------------------------------------- |
| **PermQueryEngine**              | 统一权限查询引擎 | 所有鉴权唯一入口（`service/domain/impl`）                                                    |
| **SubjectDomainService**         | 主体领域         | 合并了旧 `AbstractUserDomainService` + `AbstractRoleDomainService` + `UserRoleDomainService` |
| **AuditDomainService**           | 审计领域         | 合并了旧 `PermissionChangeDomainService` + `OperationLogDomainService`                       |
| PermissionGrantDomainService     | 权限授权领域     | 替代旧 `RolePermissionDomainService`（写操作部分）                                           |
| PermissionConditionDomainService | 权限条件评估     | 不变                                                                                         |
| PermissionConflictDomainService  | 权限冲突处理     | 不变                                                                                         |
| PermissionVersionDomainService   | 权限版本管理     | 不变                                                                                         |
| ResourceEntityDomainService      | 资源实体领域     | 不变                                                                                         |
| TypeResolutionService            | 类型解析         | 新增批量方法                                                                                 |
| DomainClassifyService            | 域分类           | 新增（替代 `bizDomainId` 实体字段）                                                          |
| MappingSyncHandler               | 接口映射同步     | 新增（service sync 策略）                                                                    |
| ResourceSyncHandler              | 资源同步处理     | 新增（service sync 策略）                                                                    |

---

## 重构结果汇总

| Phase   | 主要改动                                                                                                                                                                                      | 减少代码 | 安全修复          |
| ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ----------------- |
| Phase 1 | `PermQuery.forUserView()` + `PermViewFilter` + `PermViewAssembler` / `PermTreeAssembler` / `SnapshotAssembler`                                                                                | 新建     | 统一视图查询底层  |
| Phase 2 | `ConfigManageServiceImpl` 拆分为 5 个 `AppService`（TypeDefinition/BizDomain/DomainConfig/ServiceConfig/SystemConfig），并将 `ServiceInterfaceSyncService` 提升为独立 `ServiceSyncAppService` | ~500行   | 上帝类拆分        |
| Phase 3 | 合并 `SubjectDomainService` / `AuditDomainService` / `PermissionGrantDomainService`，删除 `AuthorizationService` 等 5 个旧 DomainService                                                      | ~300行   | 领域服务归并      |
| Phase 4 | `PermissionServiceImpl` + `PermissionViewServiceImpl` 拆分为 `PermissionCheckAppService` + `PermissionQueryAppService` + `PermissionViewAppService`                                           | ~1200行  | 核心合并闭环      |
| Phase 5 | 重命名 `Service→AppService`、`Controller` 去 `Manage` 后缀、`@OperationLog` AOP + `OperationLogRuntimeContext`                                                                                | 59文件   | 命名规范+日志统一 |

**整体完成了核心服务拆分、旧类删除、统一查询入口收口，以及入口日志机制的 AOP 化。**

---

## 已删除的类清单

| 已删除类                           | 替代方案                                                                                             |
| ---------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `EntityBatchLoadDomainService`     | 使用对应 Mapper 批量查询方法（`selectValidByIds` 等）                                                |
| `ResourcePermissionValidator`      | 使用 `PermQueryEngine`                                                                               |
| `AuthorizationService`             | canGrant 校验走 `PermissionGrantDomainService.checkCanGrant()`                                       |
| `OperationType` 枚举               | 使用 `OperationCodeConstants`                                                                        |
| `OperationLogDomainService`        | 合并入 `AuditDomainService`                                                                          |
| `PermissionChangeDomainService`    | 合并入 `AuditDomainService`                                                                          |
| `AbstractUserDomainService`        | 合并入 `SubjectDomainService`                                                                        |
| `UserRoleDomainService`            | 合并入 `SubjectDomainService`                                                                        |
| `RolePermissionDomainService`      | 读操作 → `PermQueryEngine` + `RoleResourcePermissionMapper`；写操作 → `PermissionGrantDomainService` |
| `OperationPermissionDomainService` | 直接使用 `OperationPermissionMapper` + `TypeResolutionService`                                       |
| `AbstractRoleDomainService`        | 合并入 `SubjectDomainService`                                                                        |
| `DomainConfigDomainService`        | 拆分为 `DomainClassifyService` + `DomainConfigAppService`                                            |
| `ResourceApiMappingDomainService`  | 直接使用 `ResourceApiMappingMapper`                                                                  |
| `AdvancedFeatureDomainService`     | 拆分为 `ConditionAppService` + `ConflictRuleAppService` + `DependencyAppService`                     |
| `PermissionCheckDomainService`     | 合并入 `PermQueryEngine`                                                                             |
| `ResourceDependencyDomainService`  | `DependencyAppService` + `ResourceDependencyMapper`                                                  |
| `PermCacheDomainService`           | 使用统一 `CacheService` + `PermCacheCatalog`                                                         |
| `ConfigManageServiceImpl`          | 拆分为 5 个 AppService；服务同步能力独立为 `ServiceSyncAppService`                                   |
| `PermissionServiceImpl`            | 拆分为 `PermissionCheckAppService` + `PermissionQueryAppService`                                     |

---

## 关键架构变更

1. **ConfigManageServiceImpl（12 deps）已拆分**为：`TypeDefinitionAppService` + `BizDomainAppService` + `DomainConfigAppService` + `ServiceConfigAppService` + `SystemConfigAppService`；原 `ServiceInterfaceSyncService` 已提升为独立 `ServiceSyncAppService`。各 AppService 依赖 2-7 个。
2. **RolePermEntryMapper 移到 `util` 包**（原在 `service/domain`），统一负责 `RoleResourcePermission → RolePermEntry` 的转换和操作信息填充。
3. **@OperationLog AOP** 替代手动 `asyncRecord()` 调用。`OperationLogRuntimeContext` 支持方法体内 `markSkip()`/`setSummary()`/`setTargetType()`/`setTargetId()` 覆盖注解值。
4. **bizDomainId 字段已从实体类中删除**（`AbstractRole`, `ResourceEntity`, `TypeDefinition`, `ResourceApiMapping`, `PermissionConflictRule`, `PermissionChangeLog`）。域分类通过 `DomainClassifyService` 按资源类型码间接关联。
5. **auto-grant 自动补全标记 TODO**：Phase 1-5 未实现自动补全级联逻辑。`PermissionGrantDomainService.revokePermissions` 的版本递增由调用方在 `TransactionSynchronization.afterCommit` 中负责。
6. **Gateway 权限门禁变更**：`getEffectivePermissions` 从 `SYSTEM_CONFIG.VIEW` 改为按 targetType 对应的资源类型 VIEW 权限判定。
