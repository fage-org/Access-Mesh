# Service 层复用性审查计划（更新版）

## 审查目标

分析 permission-center 的两层 Service（调度层 Service / 领域层 DomainService），识别重复逻辑，在保持扩展性的同时减少代码重复。

---

## 当前架构概览

### 调度层 Service（13个）

| Service | 主要职责 | 代码行数 |
|---------|---------|---------|
| PermissionServiceImpl | 权限检查核心接口 | ~1100行 |
| AuthorizationServiceImpl | 操作者权限校验 | ~750行 |
| PermissionGrantServiceImpl | 权限授予/撤销 | ~660行 |
| PermissionViewServiceImpl | 权限视图查询 | ~860行 |
| UserManageServiceImpl | 用户管理 | ~550行 |
| RoleManageServiceImpl | 角色管理 | - |
| ResourceManageServiceImpl | 资源管理 | - |
| ConfigManageServiceImpl | 配置管理 | - |
| OperationManageServiceImpl | 操作权限管理 | - |
| AdvancedFeatureServiceImpl | 高级特性 | - |
| PermissionVersionServiceImpl | 版本管理 | - |
| PermissionChangeLogServiceImpl | 变更日志 | - |
| OperationLogQueryServiceImpl | 操作日志查询 | - |

### 领域层 DomainService（18个）

| DomainService | 主要职责 | 缓存支持 |
|---------------|---------|---------|
| UserRoleDomainService | 用户角色解析 | L1+L2 |
| RolePermissionDomainService | 角色权限操作 | - |
| ResourceEntityDomainService | 资源实体层级操作 | - |
| TypeResolutionService | 类型解析 code↔value | - |
| PermissionVersionDomainService | 权限版本管理 | L1 |
| PermissionConflictDomainService | 权限冲突处理 | - |
| PermissionConditionDomainService | 权限条件评估 | - |
| PermissionChangeDomainService | 权限变更记录 | - |
| OperationLogDomainService | 操作日志记录 | 异步 |
| PermCacheDomainService | L1 缓存管理 | L1 |
| ResourceDependencyDomainService | 资源依赖处理 | - |
| OperationPermissionDomainService | 操作权限领域 | - |
| AbstractRoleDomainService | 抽象角色领域 | - |
| AbstractUserDomainService | 抽象用户领域 | - |
| DomainConfigDomainService | 域配置领域 | - |
| ResourceApiMappingDomainService | API 映射领域 | - |
| AdvancedFeatureDomainService | 高级特性领域 | - |
| PermissionCheckDomainService | 权限检查领域 | - |

---

## 发现的问题（按优先级排序）

### 【P0 - 高优先级】必须立即修复

#### 1. 批量加载逻辑大量重复

**问题描述**：多个 ServiceImpl 中都有相似的批量加载实体逻辑，代码重复率极高。

**重复位置统计**：

| Service | 重复次数 | 典型方法 |
|---------|---------|---------|
| PermissionServiceImpl | 8+ | queryMatchedEntries, toRolePermEntries, checkInterface, queryResources 等 |
| PermissionViewServiceImpl | 4 | loadRoles, loadOperations, loadResources, loadDomainCodes |
| PermissionGrantServiceImpl | 3 | toItemRespList, batchGrant |
| AuthorizationServiceImpl | 5 | checkPermissionsBatchForResource, checkPermissionsBatchForUser 等 |

**典型重复代码模式**：
```java
// 模式1：批量加载 OperationPermission（在至少6个方法中重复）
Set<Long> operationIds = perms.stream()
    .map(RoleResourcePermission::getOperationPermissionId)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());
Map<Long, OperationPermission> operationMap = operationIds.isEmpty() ? Map.of() :
    operationPermissionMapper.selectListByQuery(
        QueryWrapper.create().where(OPERATION_PERMISSION.ID.in(operationIds))
    ).stream().collect(Collectors.toMap(OperationPermission::getId, op -> op));

// 模式2：批量加载 ResourceEntity（在至少5个方法中重复）
Set<Long> resourceIds = perms.stream()
    .map(RoleResourcePermission::getResourceEntityId)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());
Map<Long, ResourceEntity> resourceMap = resourceIds.isEmpty() ? Map.of() :
    resourceEntityMapper.selectListByQuery(
        QueryWrapper.create()
            .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_ENTITY.ID.in(resourceIds))
            .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
    ).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));

// 模式3：批量加载 AbstractRole（在至少4个方法中重复）
// 模式4：批量加载 PermissionCondition（在至少3个方法中重复）
// 模式5：批量加载 BizDomain 获取 code（在至少2个方法中重复）
```

**优化方案**：
1. 创建 `EntityBatchLoadDomainService` 统一提供批量加载方法
2. 方法设计：
   ```java
   public interface EntityBatchLoadDomainService {
       Map<Long, OperationPermission> batchLoadOperations(Set<Long> ids);
       Map<Long, ResourceEntity> batchLoadResources(Long tenantId, Set<Long> ids);
       Map<Long, AbstractRole> batchLoadRoles(Long tenantId, Set<Long> ids);
       Map<Long, PermissionCondition> batchLoadConditions(Set<Long> ids);
       Map<Long, String> batchLoadDomainCodes(Long tenantId, Set<Long> bizDomainIds);
   }
   ```
3. 内置空集合处理，返回不可变 Map
4. 可选：集成 PermCacheDomainService 提供 L1 缓存

**预估收益**：减少 ~200 行重复代码

---

#### 2. 用户角色解析绕过 DomainService 缓存

**问题描述**：AuthorizationServiceImpl 中多处直接查询 UserRole 表，绕过了 UserRoleDomainService 的缓存机制，造成性能损失。

**问题位置**：

| 方法 | 行号 | 问题 |
|------|------|------|
| hasPermission() | 115-128 | 直接查询 user_role，未用缓存 |
| hasPermissionOnRole() | 160-177 | 直接查询 user_role，未用缓存 |
| checkPermissionOnResource() | 268-284 | 直接查询 user_role，未用缓存 |
| checkPermissionsBatchForUser() | 正确使用 | ✓ 使用 DomainService |
| checkPermissionsBatchForRole() | 正确使用 | ✓ 使用 DomainService |
| checkPermissionsBatchForResource() | 正确使用 | ✓ 使用 DomainService |

**问题代码示例**：
```java
// AuthorizationServiceImpl.java:115-128 - 直接查询，绕过缓存
List<UserRole> userRoles = userRoleMapper.selectListByQuery(
    QueryWrapper.create()
        .where(USER_ROLE.TENANT_ID.eq(tenantId))
        .and(USER_ROLE.ABSTRACT_USER_ID.eq(operatorId))
        .and(USER_ROLE.TARGET_TYPE.eq("ROLE"))
        .and(USER_ROLE.DELETE_FLAG.eq(0))
        .and(USER_ROLE.VALID_FROM.le(LocalDateTime.now()).or(USER_ROLE.VALID_FROM.isNull()))
        .and(USER_ROLE.VALID_TO.ge(LocalDateTime.now()).or(USER_ROLE.VALID_TO.isNull()))
);
// 未利用 UserRoleDomainService 的 L1+L2 缓存
```

**优化方案**：
1. AuthorizationServiceImpl 所有方法统一使用 `userRoleDomainService.resolveEffectiveRoles()`
2. 移除 private 方法中的直接 UserRole 查询
3. 简化代码：
   ```java
   // 优化后
   Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
   if (operatorRoleIds.isEmpty()) {
       return false;
   }
   ```

**预估收益**：提升缓存命中率，减少数据库查询

---

### 【P1 - 中优先级】建议尽快修复

#### 3. 权限版本计算逻辑重复

**问题描述**：计算 permissionVersion 的逻辑在 4+ 个方法中重复。

**重复位置**：
| Service | 方法 | 行号 |
|---------|------|------|
| PermissionServiceImpl | queryResources() | 483-488 |
| PermissionServiceImpl | queryScopes() | 621-625 |
| PermissionServiceImpl | interfaceSnapshot() | 699-702 |
| PermissionServiceImpl | queryPermissionTree() | 984-987 |

**重复代码**：
```java
// 在4个方法中几乎完全相同
long version = validRoleIds.stream()
    .mapToLong(roleId -> permissionVersionDomainService.getCurrentVersion(tenantId, roleId))
    .max()
    .orElse(0L);
String permissionVersion = userId + ":" + version;
```

**优化方案**：
PermissionVersionDomainService 新增方法：
```java
// 计算多个角色的最大版本号
long calculateMaxVersion(Long tenantId, Set<Long> roleIds);

// 构建完整的版本字符串（userId:version）
String buildPermissionVersionKey(Long userId, Long tenantId, Set<Long> roleIds);
```

**预估收益**：减少 ~16 行重复代码

---

#### 4. RolePermEntry 转换逻辑重复

**问题描述**：将 RoleResourcePermission 转换为 RolePermEntry 的逻辑在多处重复。

**重复位置**：
| Service | 方法 | 行号 |
|---------|------|------|
| PermissionServiceImpl | toRolePermEntries() | 638-674 |
| PermissionServiceImpl | queryMatchedEntries() | 843-858 |
| RolePermissionDomainServiceImpl | getRolePermissions() | 46-55 |

**优化方案**：
在 RolePermissionDomainService 中添加统一转换方法：
```java
List<RolePermEntry> toRolePermEntries(List<RoleResourcePermission> perms, 
                                       Map<Long, OperationPermission> opMap);
```

---

#### 5. DomainService 缺少批量方法

**问题描述**：部分 DomainService 只有单条方法，调度层不得不循环调用或重复实现批量逻辑。

**缺少批量方法的 DomainService**：

| DomainService | 缺少的批量方法 |
|---------------|---------------|
| TypeResolutionService | `batchResolveTypeValues()`, `batchResolveTypeCodes()` |
| ResourceEntityDomainService | `batchGetAncestorIds()` |
| UserRoleDomainService | `batchResolveEffectiveRoles()` |

**优化方案**：
```java
// TypeResolutionService 新增
Map<String, Integer> batchResolveTypeValues(Long tenantId, String typeKey, Set<String> codes);
Map<Integer, String> batchResolveTypeCodes(Long tenantId, String typeKey, Set<Integer> values);

// ResourceEntityDomainService 新增
Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> resourceIds);

// UserRoleDomainService 新增
Map<Long, Set<Long>> batchResolveEffectiveRoles(Long tenantId, Set<Long> userIds, Long bizDomainId);
```

---

### 【P2 - 低优先级】可延后处理

#### 6. 权限位运算逻辑分散

**问题描述**：权限位运算（effectiveBits & targetBit）逻辑分散在调度层，未下沉到实体。

**问题位置**：
| Service | 方法 | 行号 |
|---------|------|------|
| PermissionServiceImpl | queryMatchedEntries() | 847-849 |
| PermissionViewServiceImpl | checkRoleDirectGrant() | 678-700 |

**优化方案**：
OperationPermission 实体类添加方法：
```java
// 在 OperationPermission.java 中添加
public long getEffectiveBits() {
    return (binaryBit != null ? binaryBit : 0L) | (inheritMask != null ? inheritMask : 0L);
}

public boolean matchesBit(OperationPermission target) {
    long targetBit = target.binaryBit != null ? target.binaryBit : 0L;
    return targetBit != 0L && (getEffectiveBits() & targetBit) != 0;
}
```

---

#### 7. 数据转换方法应下沉

**问题描述**：调度层 Service 中的数据转换方法应下沉到 DomainService。

| Service | 方法 | 建议下沉位置 |
|---------|------|-------------|
| PermissionGrantServiceImpl | toItemRespList() | RolePermissionDomainService |
| UserManageServiceImpl | toUserResp() | AbstractUserDomainService 或实体 |

---

#### 8. 用户状态检查逻辑重复

**问题描述**：检查用户是否存在、是否启用的逻辑在多处重复。

**问题位置**：
| Service | 方法 | 行号 |
|---------|------|------|
| PermissionServiceImpl | checkInternal() | 768-770 |
| PermissionServiceImpl | checkInterface() | 187-192 |

**优化方案**：
AbstractUser 实体类添加方法：
```java
public boolean isActive() {
    return deleteFlag == 0L && Boolean.TRUE.equals(enabled);
}
```

---

## 优化执行计划

### Phase 1：创建 EntityBatchLoadDomainService（P0）

**目标**：消除批量加载逻辑重复，统一批量加载入口

**步骤**：
1. 创建 `EntityBatchLoadDomainService` 接口和实现
2. 实现 5 个核心批量加载方法
3. 修改 4 个 ServiceImpl 使用新服务
4. 测试验证

**预估改动**：
- 新增：1个 DomainService 接口 + 1个实现
- 修改：PermissionServiceImpl、PermissionViewServiceImpl、PermissionGrantServiceImpl、AuthorizationServiceImpl

---

### Phase 2：统一用户角色解析（P0）

**目标**：确保所有用户角色查询都通过 UserRoleDomainService

**步骤**：
1. AuthorizationServiceImpl 3个方法改用 DomainService
2. 移除直接 UserRole 查询的私有方法
3. UserRoleDomainServiceImpl 新增批量方法（可选）

**预估改动**：
- 修改：AuthorizationServiceImpl（简化 ~50 行）
- 可选新增：UserRoleDomainServiceImpl 批量方法

---

### Phase 3：版本计算与转换方法统一（P1）

**目标**：统一权限版本计算和 RolePermEntry 转换逻辑

**步骤**：
1. PermissionVersionDomainService 新增 2 个方法
2. RolePermissionDomainService 新增转换方法
3. 修改调度层调用

**预估改动**：
- 修改：PermissionVersionDomainServiceImpl、RolePermissionDomainServiceImpl
- 修改：PermissionServiceImpl、PermissionViewServiceImpl

---

### Phase 4：类型解析批量方法（P1）

**目标**：支持批量类型解析，避免循环调用

**步骤**：
1. TypeResolutionService 新增批量方法
2. 修改调度层使用批量方法

**预估改动**：
- 修改：TypeResolutionServiceImpl
- 修改：多个 ServiceImpl

---

### Phase 5：实体方法增强（P2）

**目标**：将简单判断逻辑下沉到实体类

**步骤**：
1. OperationPermission 添加 getEffectiveBits()、matchesBit()
2. AbstractUser 添加 isActive()
3. 修改调度层使用实体方法

---

## 预期收益汇总

| 改动类别 | 减少代码行数 | 性能提升 | 维护性提升 |
|----------|-------------|---------|-----------|
| 批量加载统一 | ~200行 | 缓存优化 | 高 |
| 用户角色解析统一 | ~50行 | 缓存命中率大幅提升 | 高 |
| 版本计算统一 | ~16行 | - | 中 |
| 转换方法下沉 | ~30行 | - | 中 |
| 批量方法补充 | - | 避免N+1风险 | 高 |
| 实体方法增强 | ~10行 | - | 低 |

**总计减少约 300+ 行重复代码，显著提升缓存利用率和代码一致性。**

---

## 扩展性保障原则

1. **调度层职责不变**：调度层仍负责编排，不做单一领域逻辑
2. **DomainService 不返回 Controller DTO**：返回领域对象或基础类型，保持领域层纯净
3. **不引入过度抽象**：批量方法按实际需求设计，不提前泛化
4. **保持现有接口签名**：对外 API 不变，只重构内部实现
5. **新增方法而非修改**：DomainService 新增辅助方法，不改变核心方法行为

---

## 不改动的范围

1. Controller 层逻辑不变
2. 对外 API 契约不变（api-contract.md）
3. 数据库表结构不变
4. Mapper 层 SQL 不变（除非优化批量操作）
5. 现有 DomainService 核心方法签名不变

---

## 下一步行动建议

**推荐执行顺序**：
1. ✅ Phase 1（P0）- EntityBatchLoadDomainService - 已完成
2. ✅ Phase 2（P0）- AuthorizationService 复用 UserRoleDomainService - 已完成
3. ✅ Phase 3（P1）- 版本计算统一 - 已完成
4. ✅ Phase 4（P1）- 类型解析批量方法 - 已完成
5. ✅ Phase 5（P2）- 实体方法增强 - 已完成

**Phase 5 完成内容**：
- OperationPermission 实体新增 `matchesBit()` 方法，下沉权限位运算逻辑
- PermissionServiceImpl 和 PermissionViewServiceImpl 使用 matchesBit() 替代手动位运算
- 用户查询改为 `selectOneByQuery` 带 tenantId 和 DELETE_FLAG 过滤，移除冗余的 deleteFlag 判断
- 测试文件已更新并全部通过

---

## 所有 Phase 完成总结

| Phase | 主要改动 | 减少代码 | 安全修复 |
|-------|---------|---------|---------|
| Phase 1 | EntityBatchLoadDomainService 统一批量加载 | ~200行 | tenant isolation + soft-delete |
| Phase 2 | AuthorizationService 复用 UserRoleDomainService | ~50行 | 缓存命中率提升 |
| Phase 3 | PermissionVersionDomainService 版本计算统一 | ~16行 | - |
| Phase 4 | TypeResolutionService/ResourceEntityDomainService/UserRoleDomainService 批量方法 | - | 3处 tenant isolation |
| Phase 5 | OperationPermission.matchesBit() + 用户查询改进 | ~10行 | 查询时过滤软删除 |

**总计减少约 276 行重复代码，修复 6 处安全/租户隔离问题。**