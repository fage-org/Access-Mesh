# Skill: 细粒度权限检查实现

> 本文档描述如何为缺少权限验证的接口添加细粒度权限控制。
> 适用于：Controller/Service 层接口缺少操作级权限检查的安全问题。

## 问题识别模式

### 症状特征

1. **Controller 方法无权限注解**：没有 `@PreAuthorize`、`@SaCheckPermission` 或类似注解
2. **Service 方法无权限验证调用**：直接操作数据，未调用 `AuthorizationService.hasPermission`
3. **请求体包含资源标识**：`resourceId`、`serviceCode`、`domainCode` 等可被任意传入
4. **仅有租户隔离**：只验证 `tenantId`，未验证操作者对具体资源的权限

### 风险等级判定

| 筇状组合 | 风险等级 | 示例 |
|---------|---------|------|
| 写操作 + 无权限验证 + 资源标识可传入 | **High/Critical** | API 映射管理、配置修改 |
| 读操作 + 无权限验证 + 资源标识可传入 | **Medium** | 敏感数据查询 |
| 写操作 + 有租户隔离 + 无资源级权限 | **Medium** | 租户内越权风险 |
| 仅内部密钥保护 + 无操作级权限 | **High** | 依赖网关信任链 |

## 分析流程

### Step 1: 确定资源类型

识别接口操作的对象属于哪种资源类型：

| 资源类型 | type_code | 典型操作 |
|---------|-----------|---------|
| 用户 | USER | CREATE, SYNC, MANAGE |
| 角色 | ROLE | CREATE, MANAGE, VIEW |
| 资源 | RESOURCE | CREATE, MANAGE, VIEW |
| 条件 | CONDITION | MANAGE |
| 冲突规则 | CONFLICT_RULE | MANAGE |
| 服务配置 | SERVICE | MANAGE_API_MAPPING, SYNC_INTERFACE |

### Step 2: 确定操作类型

| 操作类型 | code | 说明 |
|---------|------|------|
| 创建 | CREATE | 新增资源实例 |
| 管理 | MANAGE | 修改/删除/完全控制 |
| 查看 | VIEW | 只读访问 |
| 同步 | SYNC | 外部数据同步 |
| 自定义 | {RESOURCE}_MANAGE | 资源特定操作 |

### Step 3: 选择验证模式

根据场景选择：

| 模式 | 适用场景 | 实现方式 |
|------|---------|---------|
| **资源类型级** | 有权限就能操作所有同类资源 | `hasPermission(tenantId, operatorId, resourceTypeCode, operationCode)` |
| **资源实例级** | 只能操作特定授权的实例 | `checkPermissionOnResource(tenantId, operatorId, resourceId, operationCode)` |
| **角色权限级** | 对角色本身的操作权限 | `hasPermissionOnRole(tenantId, operatorId, targetRoleId, operationCode)` |

### Step 4: 定位验证插入点

在以下位置插入权限验证：

```
Controller → Service.method() {
    // ★ 权限验证入口点（方法开头）
    validatePermission(tenantId, operatorId, req);
    
    // 原有业务逻辑
    ...
}
```

## 实现模板

### 模板 A: 资源实例级权限（推荐）

适用于：只能操作特定授权的资源实例

#### 1. 定义验证接口

```java
// {Resource}PermissionValidator.java
package cn.ac.fage.accessmesh.permission.service.domain;

public interface ServiceResourceValidator {
    
    /**
     * 验证操作者是否有权限管理指定服务的 API 映射
     * @param tenantId 租户ID
     * @param operatorId 操作者ID（来自 X-User-Id Header）
     * @param serviceCode 服务编码（来自请求体）
     * @throws SecurityException 无权限时抛出
     */
    void validateApiMappingPermission(Long tenantId, Long operatorId, String serviceCode);
}
```

#### 2. 实现验证逻辑

```java
// ServiceResourceValidatorImpl.java
package cn.ac.fage.accessmesh.permission.service.domain.impl;

@Component
public class ServiceResourceValidatorImpl implements ServiceResourceValidator {

    private final ResourceEntityDomainService resourceEntityDomainService;
    private final AuthorizationService authorizationService;

    public ServiceResourceValidatorImpl(
            ResourceEntityDomainService resourceEntityDomainService,
            AuthorizationService authorizationService) {
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.authorizationService = authorizationService;
    }

    @Override
    public void validateApiMappingPermission(Long tenantId, Long operatorId, String serviceCode) {
        // 1. 参数校验
        if (tenantId == null || operatorId == null || serviceCode == null || serviceCode.isBlank()) {
            throw new IllegalArgumentException("Invalid parameters for permission check");
        }

        // 2. 查找资源实例
        Long serviceResourceId = resourceEntityDomainService.findByTypeAndCode(
            tenantId, "SERVICE", serviceCode);
        
        if (serviceResourceId == null) {
            // 资源不存在时的处理策略：
            // Option A: 拒绝操作（严格模式）
            throw new IllegalArgumentException("Service not registered: " + serviceCode);
            // Option B: 允许操作（宽松模式，适用于自动创建场景）
            // return;
        }

        // 3. 权限检查
        boolean hasPermission = authorizationService.checkPermissionOnResource(
            tenantId, operatorId, serviceResourceId, "MANAGE_API_MAPPING");

        // 4. 失败处理
        if (!hasPermission) {
            log.warn("Permission denied: operator={}, service={}, operation=MANAGE_API_MAPPING",
                operatorId, serviceCode);
            throw new SecurityException(
                "Permission denied: cannot manage API mapping for service '" + serviceCode + "'");
        }
        
        log.debug("Permission granted: operator={}, service={}", operatorId, serviceCode);
    }
}
```

#### 3. 在 Service 层调用验证

```java
// ResourceManageServiceImpl.java
@Override
@Transactional(rollbackFor = Exception.class)
public ApiMappingResp addApiMapping(Long tenantId, ApiMappingAddReq req) {
    Long operatorId = OperatorContext.getOperatorId();
    
    // ★ 权限验证（插入点）
    serviceResourceValidator.validateApiMappingPermission(tenantId, operatorId, req.serviceCode());
    
    // 原有业务逻辑
    ResourceEntity entity = resourceEntityDomainService.selectValidById(tenantId, req.resourceId());
    if (entity == null) {
        throw new IllegalArgumentException("Resource not found: " + req.resourceId());
    }
    ...
}
```

#### 4. 更新操作的特殊处理

```java
@Override
@Transactional(rollbackFor = Exception.class)
public ApiMappingResp updateApiMapping(Long tenantId, ApiMappingUpdateReq req) {
    Long operatorId = OperatorContext.getOperatorId();
    
    // ★ 验证操作对象的归属（而非请求体传入的标识）
    ResourceApiMapping existing = resourceApiMappingDomainService.selectValidById(tenantId, req.mappingId());
    if (existing == null) {
        throw new IllegalArgumentException("API mapping not found: " + req.mappingId());
    }
    
    // 使用数据库中存储的 serviceCode，而非请求体传入的值
    serviceResourceValidator.validateApiMappingPermission(
        tenantId, operatorId, existing.getServiceCode());
    
    // 原有更新逻辑
    ...
}
```

#### 5. 批量操作的验证

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void removeApiMappingsByIds(Long tenantId, List<Long> mappingIds, Long operatorId) {
    operatorId = OperatorUtil.resolveOrDefault(operatorId);
    
    if (mappingIds == null || mappingIds.isEmpty()) {
        return;
    }
    
    // ★ 批量查询并验证所有操作对象的归属
    List<ResourceApiMapping> mappings = resourceApiMappingDomainService.selectValidByIds(tenantId, mappingIds);
    
    // 收集所有涉及的 serviceCode
    Set<String> serviceCodes = mappings.stream()
        .map(ResourceApiMapping::getServiceCode)
        .collect(Collectors.toSet());
    
    // 逐一验证
    for (String serviceCode : serviceCodes) {
        serviceResourceValidator.validateApiMappingPermission(tenantId, operatorId, serviceCode);
    }
    
    // 原有删除逻辑
    ...
}
```

### 模板 B: 资源类型级权限

适用于：有权限就能操作所有同类资源

```java
// 简化验证（无需查找资源实例）
@Override
public void createCondition(Long tenantId, ConditionCreateReq req) {
    Long operatorId = OperatorContext.getOperatorId();
    
    // ★ 类型级权限检查
    boolean hasPermission = authorizationService.hasPermission(
        tenantId, operatorId, "CONDITION", "MANAGE");
    
    if (!hasPermission) {
        throw new SecurityException("Permission denied: cannot manage conditions");
    }
    
    // 原有业务逻辑
    ...
}
```

### 模板 C: 角色权限级验证

适用于：对角色本身的操作权限（如授权、查看角色权限）

```java
@Override
public RolePermissionViewResp getRolePermissionView(Long tenantId, Long roleId) {
    Long operatorId = OperatorContext.getOperatorId();
    
    // ★ 角色权限级验证
    boolean hasPermission = authorizationService.hasPermissionOnRole(
        tenantId, operatorId, roleId, "VIEW");
    
    if (!hasPermission) {
        throw new SecurityException("Permission denied: cannot view role " + roleId);
    }
    
    // 原有查询逻辑
    ...
}
```

## 数据初始化要求

### 1. 资源类型定义

```sql
-- 确保 type_definition 中存在资源类型
INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system)
VALUES ({tenantId}, 'resource_type', '{RESOURCE_TYPE_CODE}', {type_value}, '{显示名称}', true)
ON CONFLICT DO NOTHING;
```

### 2. 操作权限定义

```sql
-- 确保 operation_permission 中存在操作
INSERT INTO operation_permission (tenant_id, resource_type, code, name)
VALUES ({tenantId}, {resource_type_value}, '{OPERATION_CODE}', '{显示名称}')
ON CONFLICT DO NOTHING;
```

### 3. 资源实例（仅实例级权限需要）

```sql
-- 为每个可操作对象创建资源实例
INSERT INTO resource_entity (tenant_id, resource_type, code, name, owner_service_code)
VALUES ({tenantId}, {resource_type_value}, '{object_code}', '{显示名称}', '{owner_service}');
```

## 验收标准

| 检查项 | 验证方法 |
|--------|---------|
| 无权限调用返回错误 | 用无权限用户调用，期望 403 或 SecurityException |
| 有权限调用正常执行 | 用有权限用户调用，期望成功 |
| 日志记录验证失败 | 检查 log.warn 是否输出权限拒绝信息 |
| 批量操作完整验证 | 批量删除涉及多服务时，每个服务都需验证 |
| 更新操作使用存储值 | 更新时验证数据库中的归属，而非请求体传入值 |

## 常见问题

### Q1: 资源实例不存在怎么办？

根据业务场景选择：
- **严格模式**：拒绝操作，抛出 `IllegalArgumentException`
- **宽松模式**：允许操作，适用于资源自动创建场景
- **自动创建模式**：先创建资源实例，再验证

### Q2: 操作者 ID 如何获取？

```java
// 从可信 Header 获取（Gateway 注入）
Long operatorId = OperatorContext.getOperatorId();

// 如果 Header 缺失，抛出 SecurityException
if (operatorId == null) {
    throw new SecurityException("Cannot determine operator identity");
}
```

### Q3: 内部密钥保护够不够？

不够。`InternalApiSecretInterceptor` 只验证请求来自 Gateway，但不验证：
- 操作者是谁（X-User-Id 可被 Gateway 任意注入）
- 操作者是否有权限（依赖 Service 层验证）

必须在 Service 层添加细粒度权限检查。

### Q4: 是否需要缓存权限结果？

不建议在 Service 层缓存。`AuthorizationService` 内部已有 L1/L2 缓存机制。

### Q5: 多租户场景如何处理？

租户隔离由 `TenantContextHolder` 处理，权限验证在此基础上进行：

```java
Long tenantId = TenantContextHolder.getTenantId();  // 租户隔离
Long operatorId = OperatorContext.getOperatorId();   // 操作者身份
validatePermission(tenantId, operatorId, ...);        // 权限验证
```

## 扩展场景

### 场景 A: 新增资源类型

1. 添加 `type_definition(type_key='resource_type', type_code='{NEW_TYPE}')`
2. 添加 `operation_permission(resource_type={NEW_TYPE}, code='MANAGE')`
3. 创建 `{NewType}ResourceValidator.java`
4. 在相关 Service 方法中调用验证

### 场景 B: 新增操作类型

1. 添加 `operation_permission(resource_type={EXISTING_TYPE}, code='{NEW_OPERATION}')`
2. 在现有 Validator 中添加新方法或复用现有验证逻辑

### 场景 C: 服务间调用

服务用户（`user_type=SERVICE`）通过 Gateway 调用时：
- Gateway 从 Token/配置解析服务身份
- 注入可信 `X-User-Id`（服务用户的 internal ID）
- Service 层统一验证，无需区分用户类型

## 参考实现

- `AdvancedFeatureServiceImpl.java` - CONDITION、CONFLICT_RULE、DEPENDENCY 权限验证
- `ConfigManageServiceImpl.java` - SYSTEM_CONFIG 权限验证
- `UserManageServiceImpl.java` - USER:SYNC、USER:CREATE 权限验证
- `RoleManageServiceImpl.java` - ROLE:CREATE 权限验证
- `ResourceManageServiceImpl.java` - RESOURCE:CREATE 权限验证（待补充 API 映射权限）

---

> 本 Skill 由安全审查问题 #1（ResourceApiMappingController 权限缺失）沉淀。
> 适用于所有缺少细粒度权限检查的接口改造。