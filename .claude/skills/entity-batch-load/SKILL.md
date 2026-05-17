---
name: entity-batch-load
description: >-
  实体批量加载服务使用规范。
  TRIGGER when: 批量加载实体、批量查询 ResourceEntity/OperationPermission/AbstractRole/
  PermissionCondition/BizDomain、创建私有 load*() 方法、关键词 "batchLoad"、"批量加载"、
  "EntityBatchLoadDomainService"、"loadResources"、"loadOperations"、"loadRoles"。
origin: project
metadata:
  project: AccessMesh
  version: "1.0.0"
---

# 实体批量加载规范

`EntityBatchLoadDomainService` 提供统一的批量加载方法，带租户隔离和软删除过滤。

## API

```java
// 批量加载 OperationPermission（含 tenant 过滤）
Map<Long, OperationPermission> ops = entityBatchLoadDomainService.batchLoadOperations(tenantId, operationIds);

// 批量加载 ResourceEntity
Map<Long, ResourceEntity> resources = entityBatchLoadDomainService.batchLoadResources(tenantId, resourceIds);

// 批量加载 AbstractRole
Map<Long, AbstractRole> roles = entityBatchLoadDomainService.batchLoadRoles(tenantId, roleIds);

// 批量加载 PermissionCondition
Map<Long, PermissionCondition> conditions = entityBatchLoadDomainService.batchLoadConditions(tenantId, conditionIds);

// 批量加载 BizDomain codes
Map<Long, String> domainCodes = entityBatchLoadDomainService.batchLoadDomainCodes(tenantId, domainIds);

// 批量加载 BizDomain 完整对象
Map<Long, BizDomain> domains = entityBatchLoadDomainService.batchLoadDomains(tenantId, domainIds);
```

## 使用示例

```java
@Service
public class SomeServiceImpl {
    private final EntityBatchLoadDomainService entityBatchLoadDomainService;

    public void someMethod(Long tenantId, List<RoleResourcePermission> perms) {
        // 一次性批量加载所有需要的实体
        Set<Long> operationIds = extractOpIds(perms);
        Set<Long> resourceIds = extractResourceIds(perms);
        Set<Long> conditionIds = extractConditionIds(perms);

        Map<Long, OperationPermission> opMap = entityBatchLoadDomainService.batchLoadOperations(tenantId, operationIds);
        Map<Long, ResourceEntity> resourceMap = entityBatchLoadDomainService.batchLoadResources(tenantId, resourceIds);
        Map<Long, PermissionCondition> conditionMap = entityBatchLoadDomainService.batchLoadConditions(tenantId, conditionIds);
    }
}
```

## 禁止事项

- ❌ **禁止在 service impl 中写私有 `loadResources()` / `loadOperations()` / `loadRoles()` / `loadDomainCodes()` 方法** — 使用 `EntityBatchLoadDomainService`
- ❌ 禁止在循环中逐个查询实体 — 收集 ID 后批量加载
- ❌ 禁止直接调用 mapper 做批量查询 — 通过 `EntityBatchLoadDomainService`（确保租户隔离和软删除过滤一致）

## 与 Dual-Layer Cache 的关系

`EntityBatchLoadDomainService` 直接从 DB 查询，不经过缓存。
需要缓存的查询应使用 `CacheService` + 各模块 `CacheCatalogEntry` 常量（见 `dual-layer-cache-framework` skill）。

## 相关文件

| 文件                                    | 说明         |
| --------------------------------------- | ------------ |
| `EntityBatchLoadDomainServiceImpl.java` | 实现类       |
| `EntityBatchLoadDomainService.java`     | 接口         |
| `PermissionViewServiceImpl.java`        | 已改用此服务 |
| `PermissionGrantServiceImpl.java`       | 已改用此服务 |
| `PermissionServiceImpl.java`            | 已改用此服务 |
