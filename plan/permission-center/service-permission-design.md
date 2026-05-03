# 服务级权限控制设计

## 背景

当前 `ResourceApiMappingController` 的 API 映射管理接口缺少细粒度权限检查。
任何通过 Gateway 验证的用户/服务都可以修改任意服务的 API 映射配置。

安全风险：A 服务用户可以增改 B 服务的 API 映射，破坏权限系统的完整性。

## 设计目标

- 服务级细粒度权限控制：用户/服务只能管理自己有权限的服务配置
- 统一鉴权入口：无论管理员用户还是服务间调用，都通过 `AuthorizationService.hasPermission` 验证
- 复用现有权限模型：不引入新概念，在现有 resource_type / operation_permission 体系上扩展

## 核心设计

### 1. 用户类型与服务用户

`abstract_user.user_type` 支持两种类型（已在 schema 中定义）：

| type_code | type_value | 说明 |
|-----------|------------|------|
| USER | 1 | 人员用户，由 admin-service 管理 |
| SERVICE | 2 | 服务用户，用于服务间调用的身份识别 |

服务用户的特点：
- `external_id` = 服务编码（serviceCode）
- 创建时自动创建个人角色 `PERSONAL_{serviceCode}`
- 可被分配角色，获得相应权限
- 通过 Gateway 调用时，Gateway 从 Token/配置解析服务身份并注入可信 Header

### 2. SERVICE 资源类型

新增 `resource_type = SERVICE`，表示"服务配置"作为一种可授权的资源：

```sql
-- type_definition 新增
INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system)
VALUES (1, 'resource_type', 'SERVICE', 10, '服务配置', true);
```

### 3. 服务资源实例

每个接入服务在 `resource_entity` 中创建一个资源实例：

```sql
-- resource_entity 示例
INSERT INTO resource_entity (tenant_id, resource_type, code, name, owner_service_code)
VALUES (1, 10, 'admin-service', '管理服务', 'admin-service');

INSERT INTO resource_entity (tenant_id, resource_type, code, name, owner_service_code)
VALUES (1, 10, 'example-service', '示例服务', 'example-service');
```

资源实例与 `service_config.service_code` 对应：
- `resource_entity.code` = `service_config.service_code`
- `resource_entity.owner_service_code` = 服务自身编码（自管理场景）

### 4. 操作权限定义

为 SERVICE 资源类型定义操作：

```sql
-- operation_permission 新增
INSERT INTO operation_permission (tenant_id, resource_type, code, name)
VALUES (1, 10, 'MANAGE_API_MAPPING', '管理API映射');

INSERT INTO operation_permission (tenant_id, resource_type, code, name)
VALUES (1, 10, 'VIEW_API_MAPPING', '查看API映射');

INSERT INTO operation_permission (tenant_id, resource_type, code, name)
VALUES (1, 10, 'SYNC_INTERFACE', '同步接口');
```

### 5. 授权流程

#### 场景 A：管理员用户管理服务配置

1. 管理员被授予 `SERVICE:admin-service + MANAGE_API_MAPPING` 权限
2. 管理员调用 `/api/perm/resource-api-mapping/create`，传入 `serviceCode=admin-service`
3. Service 层调用鉴权：

```java
Long serviceResourceId = resourceEntityDomainService.findByCode(
    tenantId, "SERVICE", req.serviceCode());
boolean hasPermission = authorizationService.checkPermissionOnResource(
    tenantId, operatorId, serviceResourceId, "MANAGE_API_MAPPING");
if (!hasPermission) {
    throw new SecurityException("No permission to manage API mapping for service: " + req.serviceCode());
}
```

#### 场景 B：服务用户管理自身配置

1. 服务用户 `SERVICE:example-service` 创建时自动获得 `PERSONAL_example-service` 角色
2. 该角色被授予 `SERVICE:example-service + MANAGE_API_MAPPING` 权限
3. 服务调用 `/api/perm/service-config/sync-apis`，Gateway 注入可信 `X-User-Id`（服务用户的 internal ID）
4. Service 层调用鉴权，流程同上

### 6. Gateway Header 传递

需要新增可信 Header 传递机制：

| Header | 来源 | 说明 |
|--------|------|------|
| X-User-Id | Gateway 注入 | 用户/服务的 internal ID |
| X-User-Type | Gateway 注入 | USER 或 SERVICE（可选，用于快速判断） |

Gateway 在 `HeaderCleanFilter` 中清洗外部传入的这些 Header，防止伪造。

### 7. 权限检查方法设计

新增 DomainService 方法：

```java
// ServiceResourceValidator.java
public interface ServiceResourceValidator {
    /**
     * 验证操作者是否有权限管理指定服务的 API 映射
     * @throws SecurityException 无权限时抛出
     */
    void validateApiMappingPermission(Long tenantId, Long operatorId, String serviceCode);

    /**
     * 验证操作者是否有权限同步指定服务的接口
     * @throws SecurityException 无权限时抛出
     */
    void validateInterfaceSyncPermission(Long tenantId, Long operatorId, String serviceCode);
}
```

实现逻辑：

```java
@Override
public void validateApiMappingPermission(Long tenantId, Long operatorId, String serviceCode) {
    // 1. 查找服务资源实例
    Long serviceResourceId = resourceEntityDomainService.findByCode(tenantId, "SERVICE", serviceCode);
    if (serviceResourceId == null) {
        throw new IllegalArgumentException("Service not registered: " + serviceCode);
    }

    // 2. 检查权限
    boolean hasPermission = authorizationService.checkPermissionOnResource(
        tenantId, operatorId, serviceResourceId, "MANAGE_API_MAPPING");

    if (!hasPermission) {
        log.warn("Operator {} denied to manage API mapping for service {}", operatorId, serviceCode);
        throw new SecurityException(
            "Permission denied: cannot manage API mapping for service '" + serviceCode + "'");
    }
}
```

## 实现步骤

### Step 1: 数据初始化（SQL 或 admin-service 初始化逻辑）

1. `type_definition` 添加 `resource_type=SERVICE`
2. `operation_permission` 添加 MANAGE_API_MAPPING、VIEW_API_MAPPING、SYNC_INTERFACE
3. 为现有服务创建 `resource_entity` 实例

### Step 2: 新增 ServiceResourceValidator

1. 创建接口 `ServiceResourceValidator.java`
2. 创建实现 `ServiceResourceValidatorImpl.java`
3. 在相关 Service 方法中调用验证

### Step 3: 修改 ResourceManageServiceImpl

在以下方法开头添加权限验证：

- `addApiMapping()` → validateApiMappingPermission
- `updateApiMapping()` → validateApiMappingPermission（验证 mapping 所属服务）
- `removeApiMappingsByIds()` → 批量验证所有 mapping 所属服务

### Step 4: 修改 ConfigManageServiceImpl

- `syncServiceApis()` → validateInterfaceSyncPermission

### Step 5: Gateway Header 增强（可选）

- `HeaderEnrichFilter` 注入 X-User-Type
- `HeaderCleanFilter` 清洗 X-User-Type

## 影响范围

| 文件 | 改动类型 |
|------|----------|
| `ServiceResourceValidator.java` | 新增 |
| `ServiceResourceValidatorImpl.java` | 新增 |
| `ResourceManageServiceImpl.java` | 修改 - 添加权限验证调用 |
| `ConfigManageServiceImpl.java` | 修改 - 添加权限验证调用 |
| `ResourceEntityDomainService.java` | 可能新增 findByCode 方法 |
| `schema/permission-center.sql` | 补充初始化数据 |

## 验收标准

1. 无权限用户调用 API 映射管理接口，返回 403/SecurityException
2. 有权限用户正常管理
3. 服务用户只能管理自身服务的配置
4. 日志记录权限验证失败事件

## 后续扩展

- 可以扩展为其他服务级操作权限（如 SERVICE:MANAGE_CONFIG）
- 可以支持服务授权给其他用户/角色（多人协作管理同一服务）