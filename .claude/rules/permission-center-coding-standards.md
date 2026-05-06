---
name: permission-center-coding-standards
description: >-
  Permission Center 编码规范。
  Rule type: ALWAYS — applies to all permission-center module code changes.
  Covers: entity batch loading, PermQueryEngine, PermQuery/PermResult, RolePermEntry,
  OperationPermissionUtils, ConditionEvalUtils, role resolution, OperationCodeConstants, ResourceTypeCode.
origin: project
metadata:
  project: AccessMesh
  module: permission-center
  version: "3.0.0"
---

# Permission Center 编码规范

## 1. 批量实体加载

**MUST** 使用 `EntityBatchLoadDomainService`，禁止在 service impl 中写私有加载方法。

```java
// ✅ 正确
Map<Long, ResourceEntity> resourceMap = entityBatchLoadDomainService.batchLoadResources(tenantId, resourceIds);
Map<Long, OperationPermission> opMap = entityBatchLoadDomainService.batchLoadOperations(tenantId, opIds);
Map<Long, AbstractRole> roleMap = entityBatchLoadDomainService.batchLoadRoles(tenantId, roleIds);

// ❌ 禁止：私有 load 方法
private Map<Long, ResourceEntity> loadResources(Long tenantId, Set<Long> ids) { ... }
private Map<Long, OperationPermission> loadOperations(Set<Long> ids) { ... }
```

## 2. 权限查询 — 统一入口

**MUST** 通过 `PermQueryEngine` 进行所有权限查询和校验。

### 业务层 API（Service Impl 使用）

```java
// ✅ 正确 — 使用 PermQueryEngine 的业务层 API
engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);
engine.validateBatch(tenantId, operatorId, ResourceTypeCode.ROLE, roleIds, OperationCodeConstants.DELETE);
boolean allowed = engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.MANAGE);
Set<Long> denied = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.DOMAIN, domainIds, OperationCodeConstants.VIEW);

// ❌ 禁止 — 使用已删除的 ResourcePermissionValidator
permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationType.MANAGE);
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

// ❌ 禁止 — 直接查 DB 做权限判定
rolePermMapper.selectListByQuery(QueryWrapper.create().where(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))...)
```

## 3. RolePermEntry 构造

**MUST** 使用 `RolePermEntryMapper`。

```java
// ✅ 正确
RolePermEntry entry = rolePermEntryMapper.toEntry(perm);
RolePermEntry entry = rolePermEntryMapper.toEntryWithOpCode(perm, opCode);

// ❌ 禁止
new RolePermEntry(p.getId(), p.getAbstractRoleId(), ...)
```

## 4. 角色解析

**MUST** 通过 `UserRoleDomainService`。禁止在 service impl 中自己写角色解析逻辑。

```java
// ✅ 正确 — 单个用户
Set<Long> roles = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);

// ✅ 正确 — 批量用户
Map<Long, Set<Long>> roles = userRoleDomainService.batchResolveEffectiveRoles(tenantId, userIds, bizDomainId);

// ❌ 禁止 — 自己查 UserRole 表
userRoleMapper.selectListByQuery(...)
```

## 5. 类型解析

**MUST** 通过 `TypeResolutionService` 的批量方法。

```java
// ✅ 正确
Map<String, Integer> typeValues = typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", codes);
Map<ResourceResolveKey, Long> resourceIds = typeResolutionService.batchResolveResourceIds(tenantId, requests);
Map<String, Long> opIds = typeResolutionService.batchResolveOperationIds(tenantId, resourceTypeCode, opCodes);

// ❌ 禁止 — 循环调用单个解析方法
for (String code : codes) {
    Integer type = typeResolutionService.resolveTypeValue(tenantId, "resource_type", code);
}
```

## 6. OperationPermission 位运算

**MUST** 使用 `OperationPermissionUtils` 静态方法：

```java
// ✅ 正确
long bits = OperationPermissionUtils.effectiveBits(op);
boolean ok = OperationPermissionUtils.covers(granted, target);
List<RolePermEntry> filtered = OperationPermissionUtils.filterByOperation(entries, opCache, targetOp);
```

## 7. Condition 条件评估

**MUST** 使用 `ConditionEvalUtils` 静态方法进行子项评估：

```java
// ✅ 正确
boolean ok = ConditionEvalUtils.evalDateRange("2025-01-01", "2026-12-31");
boolean ok = ConditionEvalUtils.evalItem(jsonNode, context, ...);
```

## 8. 缓存失效

**MUST** 在事务提交后（`TransactionSynchronization.afterCommit`）失效缓存。

```java
// ✅ 正确
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        permissionVersionDomainService.increment(tenantId, roleId);
        userRoleDomainService.invalidateRoleCacheByRole(tenantId, roleId);
    }
});

// ❌ 禁止 — 事务提交前失效（缓存可能被回滚数据污染）
```

## 9. 构造函数依赖

**SHOULD** 保持构造函数依赖不超过 10 个。超过时应考虑拆分类。

已超标但标记 TODO 的类：
- `PermissionServiceImpl` (17 deps) — TODO: 拆分为 Query/Check/Tree
- `PermissionGrantServiceImpl` (19 deps) — TODO: 拆分为 Validation/Execution/Cascade
- `PermissionViewServiceImpl` (14 deps) — TODO: 拆分 View/Log
- `ConfigManageServiceImpl` (12 deps) — TODO: 拆分配置查询/配置管理/配置同步

## 10. 事务边界

- 读操作使用 `@Transactional(readOnly = true)` 
- 写操作使用 `@Transactional(rollbackFor = Exception.class)`
- 缓存写入在事务提交后（`afterCommit`）

## 11. MyBatis-Flex TableDef 使用（全模块）

**ALL MODULES MUST** 使用普通导入或 `Tables` 类，**禁止静态导入 `*TableDef` 类**。

适用模块：
- ✅ permission-center
- ✅ admin-service
- ✅ 所有使用 MyBatis-Flex 的模块

**原因**：静态导入 APT 生成的类会导致 `mvn clean` 后编译失败（死循环：import找不到类 → 编译失败 → APT无法运行 → 无法生成类）。

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

QueryWrapper qw = QueryWrapper.create()
    .where(ABSTRACT_ROLE.ID.eq(roleId));  // mvn clean 后编译失败
```

## 12. 常量类使用

### OperationCodeConstants（操作码）

**MUST** 使用 `OperationCodeConstants`，禁止使用已删除的 `OperationType` 枚举。

```java
// ✅ 正确
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;

engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);
engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.CREATE);

// ❌ 禁止 — 使用已删除的 OperationType 枚举
OperationType.MANAGE  // 类已删除
```

### ResourceTypeCode（资源类型）

**MUST** 使用 `ResourceTypeCode` 常量。

```java
// ✅ 正确
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;

engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);

// ❌ 禁止 — 使用字符串硬编码
engine.validate(tenantId, operatorId, "ROLE", roleId, "MANAGE");  // 拼写错误风险
```

## 13. 已删除的类（禁止引用）

以下类已删除，**禁止任何引用**：

| 类 | 替代方案 |
|---|---------|
| `ResourcePermissionValidator` | 使用 `PermQueryEngine` |
| `OperationType` 枚举 | 使用 `OperationCodeConstants` |
| `ResourcePermissionStrategy` 接口 | ID 转换由 Engine 内部处理 |
| `ServicePermissionStrategy` | 无需替代 |
| `DomainPermissionStrategy` | 无需替代 |
| `TypeDefPermissionStrategy` | 直接查询实体检查 |
| `PermissionCheckUtils` | 使用 `PermQueryEngine` 或 `PermResultUtils` |