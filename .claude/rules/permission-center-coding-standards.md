---
name: permission-center-coding-standards
description: >-
  Permission Center 编码规范。
  Rule type: ALWAYS — applies to all permission-center module code changes.
  Covers: layered architecture, PermQueryEngine, naming conventions, transaction boundaries,
  batch loading, operation logging, domain classification, type resolution.
origin: project
metadata:
  project: AccessMesh
  module: permission-center
  version: "4.0.0"
---

# Permission Center 编码规范

## 1. 分层职责

**MUST** 严格遵守四层架构：

```
Controller(参数适配) → AppService(编排/事务/门禁) → DomainService(领域规则) → Mapper(数据)
```

- **Controller**: 参数适配、请求验证、响应组装。不包含业务逻辑。
- **AppService**: 编排领域服务、声明事务边界、入口级权限校验。
- **DomainService**: 领域规则、缓存管理、内部逻辑复用。
- **Mapper**: 纯数据访问，不包含业务逻辑。

```java
// ✅ 正确 — AppService 编排事务和门禁
@Service
public class RoleManageAppServiceImpl implements RoleManageAppService {
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId) {
        // 1. 门禁校验
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("Permission denied");
        }
        // 2. 调用 DomainService
        Long roleId = subjectDomainService.createRole(tenantId, req.parentId(), roleType, req.externalId(), req.name());
        // 3. 返回
        return toRoleResp(abstractRoleMapper.selectOneById(roleId));
    }
}

// ❌ 禁止 — Controller 中包含业务逻辑
@PostMapping("/save")
public PermResult<RoleResp> create(@RequestBody RoleCreateReq req) {
    // 不要在 Controller 中直接操作 Mapper 或编写业务逻辑
    AbstractRole role = abstractRoleMapper.insert(...); // WRONG
}
```

## 2. 权限查询铁律

**MUST** 所有判定经过 `engine.query()` 或 `engine.hasPermission()`。
**仅**管理查询/日志查询可直查 Mapper（如 `listResources`, `listRoles`, `listChangeLogs`）。

```java
// ✅ 正确 — 权限判定走 PermQueryEngine
if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE)) {
    throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
}
engine.validateBatch(tenantId, operatorId, ResourceTypeCode.ROLE, roleIds, OperationCodeConstants.DELETE);
Set<Long> denied = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.DOMAIN, domainIds, OperationCodeConstants.VIEW);

// ✅ 正确 — 管理查询可直查 Mapper（不涉及权限判定）
List<ResourceEntity> resources = resourceEntityMapper.selectResourceListPaged(tenantId, resourceType, matchNone, offset, limit);
List<ChangeLogResp> logs = changeLogMapper.selectByTenantEntityTypeEntityId(tenantId, entityType, entityId, offset, limit);

// ❌ 禁止 — 直接查 DB 做权限判定
rolePermMapper.selectListByQuery(QueryWrapper.create().where(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))...)

// ❌ 禁止 — 使用已删除的类
ResourcePermissionValidator.validate(...); // 类已删除
PermissionCheckUtils.check(...);           // 类已删除
```

### Domain 层 API（复杂查询使用 PermQuery）

```java
// ✅ 正确 — 使用预设工厂方法
PermQuery q = PermQuery.forAuthCheck(tenantId, userId, resourceTypeCode, resourceCode, operationCode);
PermResult r = engine.query(q);
return PermResultUtils.toAuthCheckResp(r);

PermQuery q = PermQuery.forResourceQuery(tenantId, userId, resourceTypeCodes, operationCodes);
PermResult r = engine.query(q);

PermQuery q = PermQuery.forValidate(tenantId, operatorId, resourceTypeCode, resourceCode, operationCode);
PermResultUtils.validateOrThrow(engine.query(q));
```

### 异常边界（permission-center）

**MUST** 在 permission-center 新增/修改代码时明确区分三类公开异常，**不要**把所有失败都收敛为 `SecurityException`：

- `SecurityException`：仅用于操作者身份缺失、Gateway 签名失败、权限不足、越权访问等安全拒绝。
- `BizException`：用于资源不存在、业务键无效、角色已禁用、状态冲突、重复创建、配置不满足业务规则等**预期内业务拒绝**。
- `SystemException`：用于数据库/RPC/序列化/算法初始化等**非预期技术故障**，需要保留 `cause` 时必须包装。

补充限制：

- **MUST NOT** 用 `SecurityException` 表达“资源不存在”“状态不合法”“参数不满足业务约束”这类非安全问题。
- **SHOULD NOT** 在 AppService / DomainService 的公开业务分支新增裸 `IllegalArgumentException` / `IllegalStateException`。
- `IllegalArgumentException` 仅保留给私有 helper、enum/factory、框架适配层的编程契约校验。
- `IllegalStateException` 仅保留给配置缺失、启动失败、内部不变量破坏、理论不可达分支等 fail-fast 场景。
- 当前存量代码已存在混用现象；后续触达相关方法时，按本规则逐步收敛到 `BizException` / `SystemException` / `SecurityException`。

示例：

```java
// ✅ 鉴权失败
if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE)) {
    throw new SecurityException("Permission denied: MANAGE on ROLE:" + roleId);
}

// ✅ 业务拒绝（不要再新增裸 IllegalArgumentException）
if (role == null) {
    throw new BizException(errorCode, "Role not found");
}

// ✅ 技术故障包装
try {
    // serialize / rpc / io
} catch (Exception e) {
    throw new SystemException(systemErrorCode, "serialize role permission failed", e);
}
```

## 3. 命名规范

### Service 层

| 层次               | 命名规则               | 示例                                                        |
| ------------------ | ---------------------- | ----------------------------------------------------------- |
| AppService 接口    | `XxxAppService`        | `RoleManageAppService`, `PermissionGrantAppService`         |
| AppService 实现    | `XxxAppServiceImpl`    | `RoleManageAppServiceImpl`, `PermissionGrantAppServiceImpl` |
| DomainService 接口 | `XxxDomainService`     | `SubjectDomainService`, `AuditDomainService`                |
| DomainService 实现 | `XxxDomainServiceImpl` | `SubjectDomainServiceImpl`, `AuditDomainServiceImpl`        |

### Controller 层

```java
// ✅ 正确 — 不带 "Manage" 后缀
public class RoleController { }
public class UserController { }
public class ResourceController { }
public class OperationController { }
public class ConditionController { }

// ❌ 禁止 — 带 "Manage" 后缀（已重命名）
public class RoleManageController { }
public class UserManageController { }
```

### Engine 层

权限查询引擎统一使用 `PermQueryEngine`，位于 `service.domain.impl` 包。

## 4. 构造函数依赖

**SHOULD** 不设硬上限，逻辑内聚优先于依赖数量。当类职责过重时（逻辑分散、事务边界交叉、测试难以编写），考虑拆分类。

## 5. 事务边界

**MUST** 在 AppService 声明事务，DomainService 不声明事务。

```java
// ✅ 正确 — AppService 声明事务
@Service
public class RoleManageAppServiceImpl implements RoleManageAppService {
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleResp createRole(Long tenantId, RoleCreateReq req, Long operatorId) {
        // 写操作
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResp getRole(Long tenantId, Long roleId) {
        // 只读操作
    }
}

// ✅ 缓存写入在事务提交后执行
// 【铁律 P1-B】禁止业务侧（DomainService / AppService 业务方法内）手写 TransactionSynchronizationManager。
// afterCommit 注册统一由 @PermissionChange AOP（PermissionChangeAspect，框架侧）完成；
// 业务侧仅通过 PermissionChangeContext.mark* 登记影响范围。
// 落地状态（T-PERM-002，2026-06-20）：业务侧 15 处手写同步已全部消除。
// 注：permissionVersionDomainService.increment 已删除（审计 S-001 / design-review §A'-3），
// 缓存失效改由 Redis pub/sub 主动广播 PermInvalidateEvent + TTL 兜底。
//
// AppService 写方法标注 @PermissionChange，方法体内登记影响范围：
@Override
@Transactional(rollbackFor = Exception.class)
@OperationLog(...)
@PermissionChange
public List<RolePermissionItemResp> batchGrant(Long tenantId, RoleGrantReq req) {
    // ... 业务逻辑 ...
    // 登记受影响范围（mark 方法在未绑定时 no-op，越界调用安全）
    PermissionChangeContext.markRoles(tenantId, roleId);
    return toItemRespList(...);
}
// AOP afterCommit 自动执行（框架侧 PermissionChangeAspect.flush）：
//   subjectDomainService.invalidateRoleCacheByRole(tenantId, roleId);   // roleIds
//   subjectDomainService.invalidateRoleCacheBatch(tenantId, userIds);   // userIds
//   cacheService.evictBatch(CONDITION_RULES, tenantId, conditionIds);   // conditionIds
//   cacheService.evictBatch(ROLE_PERM_SNAPSHOT, tenantId, roleIds);     // roleSnapshotIds
//   redissonClient.getTopic("perm:invalidate").publish(PermInvalidateEvent);

// ✅ mark API（PermissionChangeContext，ThreadLocal 累积器，同 TenantContextHolder 语义）：
//   markRoles(tenantId, roleIds|roleId)        // 角色权限变更 → 失效 EFFECTIVE_ROLES
//   markUsers(tenantId, userIds)               // 用户角色关系变更 → 失效 EFFECTIVE_ROLES
//   markConditions(tenantId, conditionIds)     // 条件规则变更 → 失效 CONDITION_RULES
//   markRoleSnapshots(tenantId, roleIds)       // 角色删除 → 直清 ROLE_PERM_SNAPSHOT

// ❌ 禁止 — 业务侧手写同步（违反 P1-B，已由 @PermissionChange AOP 取代）
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { subjectDomainService.invalidateRoleCacheByRole(...); }
    });
}

// ❌ 禁止 — 事务提交前失效缓存（缓存可能被回滚数据污染）
cacheService.evict(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tenantId, roleId);
// 如果后续回滚，缓存已被错误清理
```

## 6. 批量实体加载

**MUST** 使用对应的 Mapper 批量查询方法，**禁止**在 service impl 中写私有 `load*()` 方法。

```java
// ✅ 正确 — 使用 Mapper 批量查询方法
Map<Long, ResourceEntity> resourceMap = resourceEntityMapper.selectValidByIds(tenantId, resourceIds)
    .stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));
Map<Long, OperationPermission> opMap = operationPermissionMapper.selectValidByIds(tenantId, opIds)
    .stream().collect(Collectors.toMap(OperationPermission::getId, op -> op));
Map<Long, AbstractRole> roleMap = abstractRoleMapper.selectValidByIds(tenantId, roleIds)
    .stream().collect(Collectors.toMap(AbstractRole::getId, role -> role));

// ✅ 正确 — 按资源类型批量加载操作权限
Map<Integer, List<OperationPermission>> opByType = new LinkedHashMap<>();
for (Integer resourceType : resourceTypes) {
    opByType.put(resourceType, operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType));
}

// ❌ 禁止：私有 load 方法
private Map<Long, ResourceEntity> loadResources(Long tenantId, Set<Long> ids) { ... }
private Map<Long, OperationPermission> loadOperations(Set<Long> ids) { ... }

// ❌ 禁止：使用已删除的 EntityBatchLoadDomainService
entityBatchLoadDomainService.batchLoadResources(...);  // 类已删除
```

## 7. 操作日志

### 入口级日志

**MUST** 使用 `@OperationLog` AOP 注解记录入口级操作日志。

```java
// ✅ 正确 — 使用 @OperationLog 注解
@Override
@Transactional(rollbackFor = Exception.class)
@OperationLog(module = "perm", action = "BATCH_GRANT", targetType = "abstract_role",
    targetId = "#req.roleExternalId", summary = "save granted role perms")
public List<RolePermissionItemResp> batchGrant(Long tenantId, RoleGrantReq req) {
    // 业务逻辑
    // 不再需要手动调用 auditDomainService.asyncRecordLog(...) 做入口级日志
}

// ❌ 禁止 — 在 AppService 中手动调用 asyncRecordLog 做入口级日志
auditDomainService.asyncRecordLog("perm", "BATCH_GRANT", ...); // 应由 @OperationLog 替代
```

### 内部动态日志

**MUST** 通过 `AuditDomainService` 显式调用记录内部动态日志（diff 快照、冲突通知、变更记录）。

```java
// ✅ 正确 — 内部变更日志仍显式调用
auditDomainService.recordChangeLog(new AuditDomainService.ChangeLogContext(
    tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "abstract-role-batch-remove"),
    List.of(new AuditDomainService.ChangeLogEntry(
        "abstract_role", 0L, "BATCH_DELETE", null, null, diffSnapshot,
        new Long[0], roleArr
    ))
);

// ✅ 正确 — 冲突通知仍显式调用（auditDomainService 不通过 AOP 处理）
auditDomainService.asyncRecordLog(...); // 仅在非入口级场景
```

## 8. 同层禁止横向调用

**MUST NOT** AppService 之间不得互相注入。

```java
// ❌ 禁止 — AppService 注入另一个 AppService
@Service
public class RoleManageAppServiceImpl implements RoleManageAppService {
    private final PermissionGrantAppService permissionGrantAppService; // WRONG
}

// ✅ 正确 — AppService 注入 DomainService 或 Engine
@Service
public class RoleManageAppServiceImpl implements RoleManageAppService {
    private final SubjectDomainService subjectDomainService;  // OK
    private final PermQueryEngine engine;                     // OK
    private final AbstractRoleMapper abstractRoleMapper;      // OK
}
```

## 9. 业务域分类

**MUST** 通过 `DomainClassifyService` 进行管理查询的域范围过滤。权限查询管线不感知业务域。

```java
// ✅ 正确 — 管理查询按域判断资源类型是否可见
boolean visible = domainClassifyService.matchesTypeCode(
    tenantId, DomainQueryMode.GLOBAL_PLUS, "HR", ResourceTypeCode.USER
);

// ✅ 正确 — 获取域声明的类型码范围
Set<String> typeCodes = domainClassifyService.getClassifiedTypeCodes(tenantId, "HR");

// ✅ 正确 — 通过资源类型码反查域
Long domainId = domainClassifyService.findDomainIdByTypeCode(tenantId, "ORG");

// ❌ 禁止 — 在实体上使用 bizDomainId 字段（已从 abstract_role, resource_entity 等表中删除）
role.setBizDomainId(domainId);  // 字段已删除
role.getBizDomainId();           // 字段已删除
```

### 查询模式

| 模式          | 含义                                           |
| ------------- | ---------------------------------------------- |
| `ALL`         | 不过滤，查看全部                               |
| `GLOBAL_PLUS` | 全局域 + 指定域（指定域类型 + 未被认领的类型） |
| `DOMAIN_ONLY` | 仅指定域声明的类型                             |

### 全局域

- 每个租户有且仅有一个全局域（`biz_domain.global = true`）
- 全局域的范围隐式包含未被其他域认领的资源类型，无需配置 CLASSIFY
- `DomainClassifyService` 仅负责查询与匹配，不再承担全局域创建职责

## 10. 类型解析

**MUST** 通过 `TypeResolutionService` 的批量方法，**禁止**循环调用单个解析方法。

```java
// ✅ 正确 — 批量方法
Map<String, Integer> typeValues = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", codes);
Map<ResourceResolveKey, Long> resourceIds = typeResolutionService.batchResolveResourceIds(tenantId, requests);
Map<String, Long> opIds = typeResolutionService.batchResolveOperationIds(tenantId, resourceTypeCode, opCodes);

// ❌ 禁止 — 循环调用单个解析方法
for (String code : codes) {
    Integer type = typeResolutionService.resolveTypeValue(tenantId, "resource_type", code);
}
```

## 11. 角色解析

**MUST** 通过 `SubjectDomainService` 进行用户-角色关系查询。

```java
// ✅ 正确
Set<Long> roles = userRoleDomainService.resolveEffectiveRoles(tenantId, userId);
Map<Long, Set<Long>> roles = userRoleDomainService.batchResolveEffectiveRoles(tenantId, userIds);

// ❌ 禁止 — 自己查 UserRole 表
userRoleMapper.selectListByQuery(...)
```

## 12. OperationPermission 位运算

**MUST** 使用 `OperationPermissionUtils` 静态方法。

```java
// ✅ 正确
long bits = OperationPermissionUtils.effectiveBits(op);
boolean ok = OperationPermissionUtils.covers(granted, target);
List<RolePermEntry> filtered = OperationPermissionUtils.filterByOperation(entries, opCache, targetOp);
```

## 13. RolePermEntry 构造

**MUST** 使用 `RolePermEntryMapper`。

```java
// ✅ 正确
RolePermEntry entry = rolePermEntryMapper.toEntry(perm);
RolePermEntry entry = rolePermEntryMapper.toEntryWithOpCode(perm, opCode);

// ❌ 禁止
new RolePermEntry(p.getId(), p.getAbstractRoleId(), ...)
```

## 14. Condition 条件评估

**MUST** 使用 `ConditionEvalUtils` 静态方法。

```java
// ✅ 正确
boolean ok = ConditionEvalUtils.evalDateRange("2025-01-01", "2026-12-31");
boolean ok = ConditionEvalUtils.evalItem(jsonNode, context, ...);
```

## 15. MyBatis-Flex TableDef 使用（全模块）

**ALL MODULES MUST** 使用普通导入或 `Tables` 类，**禁止静态导入 `*TableDef` 类**。

```java
// ✅ 正确 — 使用 Tables 类（APT 生成）
import cn.ac.fage.accessmesh.permission.entity.table.Tables;

QueryWrapper qw = QueryWrapper.create()
    .where(Tables.ABSTRACT_ROLE.ID.eq(roleId));

// ✅ 正确 — 普通导入 + 类名引用
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;

QueryWrapper qw = QueryWrapper.create()
    .where(AbstractRoleTableDef.ABSTRACT_ROLE.ID.eq(roleId));

// ❌ 禁止 — 静态导入
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
// mvn clean 后编译失败
```

## 16. 常量类使用

### OperationCodeConstants（操作码）

**MUST** 使用 `OperationCodeConstants`，禁止使用已删除的 `OperationType` 枚举。

```java
// ✅ 正确
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;

engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);
engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.CREATE);

// ❌ 禁止
OperationType.MANAGE  // 类已删除
```

### ResourceTypeCode（资源类型）

**MUST** 使用 `ResourceTypeCode` 常量，禁止字符串硬编码。

```java
// ✅ 正确
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;

engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);

// ❌ 禁止
engine.hasPermission(tenantId, operatorId, "ROLE", roleId, "MANAGE");  // 拼写错误风险
```

## 17. 已删除的类（禁止引用）

| 类                                                | 替代方案                                                     |
| ------------------------------------------------- | ------------------------------------------------------------ |
| `EntityBatchLoadDomainService`                    | 使用对应 Mapper 批量查询方法                                 |
| `EntityBatchLoadDomainServiceImpl`                | 使用对应 Mapper 批量查询方法                                 |
| `ResourcePermissionValidator`                     | 使用 `PermQueryEngine`                                       |
| `OperationType` 枚举                              | 使用 `OperationCodeConstants`                                |
| `ResourcePermissionStrategy` 接口                 | ID 转换由 Engine 内部处理                                    |
| `ServicePermissionStrategy`                       | 无需替代                                                     |
| `DomainPermissionStrategy`                        | 无需替代                                                     |
| `TypeDefPermissionStrategy`                       | 直接查询实体检查                                             |
| `PermissionCheckUtils`                            | 使用 `PermQueryEngine` 或 `PermResultUtils`                  |
| `AuthorizationService`                            | 已删除（Phase 3），授权校验已合并到各 AppService             |
| `ConfigManageController` / `ConfigManageService`  | 已拆分为 TypeDefinitionController + TypeDefinitionAppService |
| `*ManageService` / `*ManageServiceImpl`（旧命名） | 已重命名为 `*AppService` / `*AppServiceImpl`                 |
| `*ManageController`（旧命名）                     | 已重命名为 `*Controller`                                     |

## 18. 已删除的实体字段（禁止引用）

以下实体类的 `bizDomainId` 字段已删除：

| 实体类                   | 说明                                                                  |
| ------------------------ | --------------------------------------------------------------------- |
| `AbstractRole`           | 角色不再内嵌域归属，通过 `DomainClassifyService` 按资源类型码间接关联 |
| `ResourceEntity`         | 同上                                                                  |
| `TypeDefinition`         | 同上                                                                  |
| `ResourceApiMapping`     | 同上                                                                  |
| `PermissionConflictRule` | 同上                                                                  |
| `PermissionChangeLog`    | 同上                                                                  |

域分类通过 `domain_config` 表的 `CLASSIFY` 配置实现，参见 §9。

## 19. AI 代码生成检查清单

在 permission-center 模块生成或修改代码时，**必须**逐项检查：

### 编码前检查

| # | 检查点 | 参考 |
|---|--------|------|
| 1 | 是否有可复用的 DomainService 方法？ | §6 批量实体加载、§8 同层禁止横向调用 |
| 2 | 命名是否符合分层规范？ | §3 命名规范 |
| 3 | 是否引用了已删除的类？ | §17 已删除的类 |
| 4 | 是否引用了已删除的实体字段（如 `bizDomainId`）？ | §18 已删除的实体字段 |
| 5 | 批量操作是否使用 Mapper 批量方法（禁止循环单条）？ | §6 批量实体加载、§10 类型解析 |
| 6 | 权限判定是否走 `engine.query()` / `engine.hasPermission()`？ | §2 权限查询铁律 |

### 编码后检查

| # | 检查点 | 参考 |
|---|--------|------|
| 7 | 事务边界是否在 AppService 声明？ | §5 事务边界 |
| 8 | 异常类型是否正确（BizException / SystemException / SecurityException）？ | §2 异常边界 |
| 9 | 操作日志是否使用 `@OperationLog` AOP（入口级）或 `AuditDomainService`（内部动态）？ | §7 操作日志 |
| 10 | 缓存失效是否绑定事务提交后执行（`evictAfterCommit`）？ | §5 事务边界 |
| 11 | 是否避免了 AppService 间横向注入？ | §8 同层禁止横向调用 |
| 12 | 业务域过滤是否通过 `DomainClassifyService`（而非直查 `bizDomainId`）？ | §9 业务域分类 |

### 文档与提交检查

| # | 检查点 | 参考 |
|---|--------|------|
| 13 | 文档使用中文标题和描述 | 项目约定 |
| 14 | Commit message 格式：`<type>(<scope>): <中文描述>` | Conventional Commits |
| 15 | 提交前通过 `mvn compile` + `mvn test` | 提交前验证要求 |

### 过度设计警示

生成代码时避免以下倾向：

| 倾向 | 替代做法 |
|------|----------|
| 过度抽象（多余接口/策略类） | 保持扁平，必要时才分层 |
| 多层继承 | 优先接口 + 单实现 |
| 过多策略类 | 统一入口替代（如 PermQueryEngine） |
| 过度泛型 | 具体类型优先 |
| DDD 重架构轻实效 | 渐进式重构，Revert 不合适的模块 |
