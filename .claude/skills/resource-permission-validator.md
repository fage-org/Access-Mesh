# resource-permission-validator

Universal resource permission validator with operation type as enum parameter.

## API

```java
// 单实例校验（无权限抛 SecurityException）
permissionValidator.validate(tenantId, operatorId, "SERVICE", serviceCode, OperationType.MANAGE_API_MAPPING);

// 批量校验（任意一个无权限抛 SecurityException）
permissionValidator.validateBatch(tenantId, operatorId, "ROLE", roleIds, OperationType.DELETE);

// 非抛出检查（返回 boolean）
boolean allowed = permissionValidator.hasPermission(tenantId, operatorId, "USER", userId, OperationType.MANAGE);

// 获取被拒绝的 ID（批量非抛出）
Set<Long> denied = permissionValidator.getDeniedIds(tenantId, operatorId, "DOMAIN", domainIds, OperationType.VIEW);
```

## OperationType 枚举

```java
CREATE, VIEW, MANAGE, UPDATE, DELETE, ASSIGN, REVOKE, SYNC, MANAGE_API_MAPPING, SYNC_INTERFACE, GRANT
```

## Strategy 只负责 ID 转换

Strategy 的唯一职责是将业务 ID 转换为 `resource_entity.code`：

| 资源类型 | 业务 ID | Strategy 转换 |
|----------|---------|---------------|
| SERVICE | serviceCode (String) | 直接作为 code |
| DOMAIN | bizDomainId (Long) | bizDomainId → bizDomain.code |
| TYPE_DEFINITION | typeDefId (Long) | typeDefId → typeDef.typeCode |
| USER/ROLE/RESOURCE | 直接是 Long | 无需 Strategy（默认处理） |

## 业务规则在业务层处理

**重要：** Strategy 只负责 ID 转换，业务规则（如 isSystem 不能删除）在业务层处理：

```java
// 正确做法：权限校验 + 业务规则检查分离
public void deleteType(Long tenantId, Long typeId, Long operatorId) {
    // 1. 权限校验
    permissionValidator.validate(tenantId, operatorId, "TYPE_DEFINITION", typeId, OperationType.MANAGE);

    // 2. 业务规则检查（isSystem 不能删除）
    if (typeDefPermissionStrategy.isSystemType(tenantId, typeId)) {
        throw new IllegalStateException("Cannot delete system type");
    }

    // 3. 执行删除
    typeDefinitionMapper.softDelete(typeId);
}

// 批量操作：先校验权限，再过滤业务规则
public void deleteTypesBatch(Long tenantId, Set<Long> typeIds, Long operatorId) {
    // 1. 批量权限校验（无权限的会抛异常）
    permissionValidator.validateBatch(tenantId, operatorId, "TYPE_DEFINITION", typeIds, OperationType.MANAGE);

    // 2. 查询并过滤业务规则
    List<TypeDefinition> entities = typeDefMapper.selectListByIds(typeIds);
    Set<Long> deletableIds = entities.stream()
        .filter(e -> !Boolean.TRUE.equals(e.getIsSystem()))
        .map(TypeDefinition::getId)
        .collect(Collectors.toSet());

    // 3. 执行批量删除
    typeDefMapper.softDeleteBatch(deletableIds);
}
```

## 新增资源类型

### 情况 1： resourceId IS resource_entity.id（无需 Strategy）

大多数资源类型直接使用，无需任何代码：

```java
// resourceId 直接是 resource_entity.id
permissionValidator.validate(tenantId, operatorId, "PROJECT", projectId, OperationType.MANAGE);
```

### 情况 2： resourceId 需要转换（需要 Strategy）

当 resourceId 不是 resource_entity.id 时，注册 Strategy：

```java
@Component
public class ProjectPermissionStrategy implements ResourcePermissionStrategy<Long> {

    @Override
    public String getResourceTypeCode() {
        return "PROJECT";
    }

    @Override
    public String toResourceEntityCode(Long tenantId, Long projectId) {
        Project project = projectMapper.selectOneById(projectId);
        return project != null ? project.getExternalId() : null;  // project.externalId → resource_entity.code
    }
}
```

Strategy 自动通过 Spring DI 注册，无需手动配置。

## 方法选择

| 场景 | 方法 |
|------|------|
| 操作前强制校验 | `validate` |
| 批量操作前校验 | `validateBatch` |
| UI 显示控制（显示/隐藏按钮） | `hasPermission` |
| 业务逻辑分支 | `hasPermission` |
| 部分执行（只操作有权限的） | `getDeniedIds` |

## AuthorizationService 现仅用于 canGrant

**AuthorizationService** 现在只保留权限委托检查：

```java
// 检查是否可以授予他人权限（canGrant=true）
boolean canGrant = authorizationService.canGrantPermission(
    tenantId, operatorId, "ROLE", roleCode, "MANAGE", false, domainCode);
```

**所有通用权限校验都使用 ResourcePermissionValidator：**

```java
// TYPE-level 权限检查（如 CREATE）
permissionValidator.hasPermission(tenantId, operatorId, "ROLE", null, OperationType.CREATE);

// Instance-level 权限检查
permissionValidator.validate(tenantId, operatorId, "ROLE", roleId, OperationType.MANAGE);

// 批量权限检查
Set<Long> deniedIds = permissionValidator.getDeniedIds(tenantId, operatorId, "ROLE", roleIds, OperationType.MANAGE);
```

## USER 资源的特殊处理

USER 资源的 MANAGE 权限是 type-level（全局），但需要处理 self-modification：

```java
// 自我修改总是允许
if (operatorId.equals(targetUserId)) {
    // 允许操作
} else {
    permissionValidator.validate(tenantId, operatorId, "USER", null, OperationType.MANAGE);
}
```

## PermissionCheckUtils 工具类

提供批量权限检查的便捷方法：

```java
// 批量检查用户 MANAGE 权限（含 self-modification）
PermissionBatchResult result = PermissionCheckUtils.checkCanManageUsersWithSelfModification(
    permissionValidator, tenantId, operatorId, targetUserIds);

// 批量检查角色 MANAGE 权限
PermissionBatchResult result = PermissionCheckUtils.checkCanManageRoles(
    permissionValidator, tenantId, operatorId, targetRoleIds);

// 校验并抛出异常
PermissionCheckUtils.validateCanManageRolesOrThrow(
    permissionValidator, tenantId, operatorId, targetRoleIds);
```

## 相关文件

- `ResourcePermissionValidator.java` - 通用验证器
- `ResourcePermissionStrategy.java` - ID 转换策略接口
- `OperationType.java` - 操作类型枚举
- `ServicePermissionStrategy.java` - SERVICE 策略
- `DomainPermissionStrategy.java` - DOMAIN 策略
- `TypeDefPermissionStrategy.java` - TYPE_DEFINITION 策略（含 `isSystemType()` 业务助手）
- `AuthorizationService.java` - 仅 canGrant 权限委托检查
- `PermissionCheckUtils.java` - 批量权限检查工具类