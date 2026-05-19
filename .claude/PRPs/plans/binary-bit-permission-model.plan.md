# Plan: BinaryBit Permission Model

## Summary
重构权限查询模型，使用 binaryBit 位运算替代 operation_permission_id 存储，使 inheritMask 语义继承在数据库查询阶段生效，提高权限判定效率。

## User Story
As a Permission Center developer, I want to replace operation_permission_id storage with binaryBit and use PostgreSQL bit operations, so that inheritMask semantic inheritance works correctly and permission queries are more efficient.

## Problem → Solution
**Current**: operation_permission_id 存储外键，查询精确匹配，inheritMask 继承语义仅内存计算
**Desired**: granted_bits 存储位值，PostgreSQL 位操作查询，inheritMask 动态计算覆盖集

## Metadata
- **Complexity**: Large
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 12

---

## UX Design

### Before
N/A — 内部架构变更

### After
N/A — 内部架构变更

### Interaction Changes
N/A — 内部架构变更

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `perm-entity/.../RoleResourcePermission.java` | 1-122 | 当前实体定义，需修改 |
| P0 (critical) | `perm-entity/.../OperationPermission.java` | 1-128 | 位运算设计，binaryBit/inheritMask |
| P0 (critical) | `permission-center/.../PermQueryEngine.java` | 1-600 | 核心查询引擎，重构入口 |
| P1 (important) | `permission-center/.../RolePermSnapshot.java` | 59-124 | RolePermEntry 定义，需修改 |
| P1 (important) | `permission-center/.../RolePermEntryMapper.java` | all | Entry 转换逻辑 |
| P1 (important) | `permission-center/.../PermCacheCatalog.java` | 1-190 | 缓存目录，需新增条目 |
| P2 (reference) | `permission-center/.../RoleResourcePermissionMapper.java` | 1-270 | Mapper 接口，新增位操作方法 |
| P2 (reference) | `permission-center/.../OperationPermissionMapper.java` | 35-47 | 位操作查询示例 |

---

## Patterns to Mirror

### ENTITY_PATTERN
// SOURCE: `perm-entity/.../RoleResourcePermission.java:25-122`
```java
@Getter
@Setter
@Table("role_resource_permission")
public class RoleResourcePermission {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long tenantId;
    private Long abstractRoleId;
    // ... 字段命名：驼峰，注释：JavaDoc
}
```

### CACHE_CATALOG_PATTERN
// SOURCE: `permission-center/.../PermCacheCatalog.java:44-52`
```java
public static final CacheCatalogEntry<Set<Long>> EFFECTIVE_ROLES =
    CacheCatalogEntry.<Set<Long>>builder()
        .code("perm:effective-roles")
        .mode(CacheMode.L1_L2)
        .l1TtlMinutes(5)
        .l1MaxSize(2000)
        .l2TtlMinutes(30)
        .valueType(new TypeRef<Set<Long>>() {})
        .build();
```

### BIT_OPERATION_QUERY_PATTERN
// SOURCE: `permission-center/.../OperationPermissionMapper.java:35-47`
```java
@Select("""
    SELECT id, tenant_id, resource_type, code, name, binary_bit, inherit_mask, ...
    FROM operation_permission
    WHERE tenant_id = #{tenantId}
      AND delete_flag = 0
      AND (#{resourceType} IS NULL OR resource_type = #{resourceType})
      AND ((binary_bit | inherit_mask) & #{requiredBits}) = #{requiredBits}
    """)
List<OperationPermission> selectByEffectiveBitsMatch(...);
```

### SERVICE_PATTERN
// SOURCE: `permission-center/.../EntityBatchLoadDomainService.java:19-92`
```java
public interface EntityBatchLoadDomainService {
    /**
     * 批量加载操作权限
     * <p>
     * 根据ID集合批量查询操作权限，自动过滤租户和软删除记录
     * </p>
     */
    Map<Long, OperationPermission> batchLoadOperations(Long tenantId, Set<Long> ids);
}
```

### ENTRY_RECORD_PATTERN
// SOURCE: `permission-center/.../RolePermSnapshot.java:59-124`
```java
public record RolePermEntry(
    Long permissionId,
    Long roleId,
    Long resourceEntityId,
    String resourceCode,
    Integer resourceType,
    Long operationPermissionId,  // ← 将改为 grantedBits
    String operationCode,
    Long effectiveBits,
    ...
) {}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `perm-entity/.../RoleResourcePermission.java` | UPDATE | 替换 operation_permission_id 为 granted_bits |
| `perm-entity/.../OperationPermission.java` | REFERENCE | 参考位运算设计（不修改） |
| `permission-center/.../OperationPermissionCacheService.java` | CREATE | 新增操作权限缓存服务 |
| `permission-center/.../PermCacheCatalog.java` | UPDATE | 新增 OPERATION_PERMISSIONS_BY_TYPE 缓存条目 |
| `permission-center/.../RoleResourcePermissionMapper.java` | UPDATE | 新增位操作查询方法 |
| `permission-center/.../RoleResourcePermissionMapper.xml` | UPDATE | 位操作 SQL 实现 |
| `permission-center/.../RolePermSnapshot.java` | UPDATE | RolePermEntry.grantedBits 替代 operationPermissionId |
| `permission-center/.../RolePermEntryMapper.java` | UPDATE | Entry 转换逻辑适配 granted_bits |
| `permission-center/.../PermQueryEngine.java` | UPDATE | 重构 query() 使用位操作查询 |
| `permission-center/.../ResolveContext.java` | UPDATE | 新增 binaryBit 解析方法 |
| `test/.../PermQueryEngineTest.java` | CREATE | inheritMask 语义测试 |
| `test/.../OperationPermissionCacheServiceTest.java` | CREATE | 缓存服务测试 |

## NOT Building

- 历史数据迁移脚本（项目未上线）
- 前端 UI 变更（内部架构）
- API 接口变更（兼容现有接口）
- 权限授予逻辑重构（仅查询侧）

---

## Step-by-Step Tasks

### Task 1: 修改 RoleResourcePermission Entity
- **ACTION**: 替换 operation_permission_id 字段为 granted_bits
- **IMPLEMENT**: 
  ```java
  // 删除
  // private Long operationPermissionId;
  
  // 新增
  /**
   * 授予的操作位值
   * <p>
   * 存储 OperationPermission.binaryBit 值。
   * 用于 PostgreSQL 位操作查询：WHERE granted_bits & target_mask != 0
   * </p>
   */
  private Long grantedBits;
  ```
- **MIRROR**: ENTITY_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: granted_bits 存 binaryBit（单个位值），不是 effectiveBits
- **VALIDATE**: mvn compile perm-entity 模块成功

### Task 2: 修改 RolePermEntry Record
- **ACTION**: 替换 operationPermissionId 为 grantedBits
- **IMPLEMENT**:
  ```java
  public record RolePermEntry(
      Long permissionId,
      Long roleId,
      Long resourceEntityId,
      String resourceCode,
      Integer resourceType,
      // Long operationPermissionId,  // ← 删除
      Long grantedBits,               // ← 新增
      String operationCode,
      Long effectiveBits,
      ...
  ) {}
  ```
- **MIRROR**: ENTRY_RECORD_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: 同时保留 operationCode 用于展示（从 OperationPermission 反查）
- **VALIDATE**: mvn compile permission-center 成功

### Task 3: 新增 PermCacheCatalog 条目
- **ACTION**: 新增 OPERATION_PERMISSIONS_BY_TYPE 缓存条目
- **IMPLEMENT**:
  ```java
  /**
   * 操作权限按资源类型缓存
   * <p>
   * Key: resourceType
   * Value: Map<Long, OperationPermission> (id → op)
   * 缓存时间：60分钟（操作定义通常不变）
   * </p>
   */
  public static final CacheCatalogEntry<Map<Long, OperationPermission>> OPERATION_PERMISSIONS_BY_TYPE =
      CacheCatalogEntry.<Map<Long, OperationPermission>>builder()
          .code("perm:operation-permissions-by-type")
          .mode(CacheMode.L1_L2)
          .l1TtlMinutes(60)
          .l1MaxSize(100)
          .l2TtlMinutes(120)
          .valueType(new TypeRef<Map<Long, OperationPermission>>() {})
          .build();
  ```
- **MIRROR**: CACHE_CATALOG_PATTERN
- **IMPORTS**: 已有
- **GOTCHA**: 使用 L1_L2 双层缓存，操作定义变更少
- **VALIDATE**: 编译成功

### Task 4: 创建 OperationPermissionCacheService
- **ACTION**: 新增操作权限缓存服务
- **IMPLEMENT**:
  ```java
  @Service
  public class OperationPermissionCacheService {
      private final OperationPermissionMapper operationPermissionMapper;
      private final CacheService cacheService;
      
      /**
       * 按资源类型加载所有操作权限
       */
      public Map<Long, OperationPermission> loadByResourceType(Long tenantId, Integer resourceType) {
          String cacheKey = "op_perm:" + resourceType;
          Map<Long, OperationPermission> cached = cacheService.get(
              PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, cacheKey);
          if (cached != null) return cached;
          
          List<OperationPermission> ops = operationPermissionMapper.selectByTenantAndResourceType(
              tenantId, resourceType);
          Map<Long, OperationPermission> opMap = ops.stream()
              .collect(Collectors.toMap(OperationPermission::getId, op -> op));
          cacheService.put(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, 
              tenantId, cacheKey, opMap);
          return opMap;
      }
      
      /**
       * 计算覆盖目标操作的所有 binaryBit 值
       */
      public Set<Long> computeCoveringBits(Long tenantId, Integer resourceType, Long targetBinaryBit) {
          Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
          Set<Long> coveringBits = new HashSet<>();
          for (OperationPermission op : opMap.values()) {
              long effectiveBits = op.getEffectiveBits();
              if ((effectiveBits & targetBinaryBit) != 0) {
                  coveringBits.add(op.getBinaryBit());
              }
          }
          return coveringBits;
      }
      
      /**
       * 计算目标位掩码（用于数据库查询）
       */
      public long computeTargetBitMask(Long tenantId, Integer resourceType, Long targetBinaryBit) {
          Set<Long> coveringBits = computeCoveringBits(tenantId, resourceType, targetBinaryBit);
          long mask = 0L;
          for (Long bit : coveringBits) mask |= bit;
          return mask;
      }
      
      /**
       * 按 binaryBit 查找 OperationPermission
       */
      public OperationPermission findByBinaryBit(Long tenantId, Integer resourceType, Long binaryBit) {
          Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
          return opMap.values().stream()
              .filter(op -> Objects.equals(op.getBinaryBit(), binaryBit))
              .findFirst().orElse(null);
      }
      
      /**
       * 操作码转 binaryBit
       */
      public Long codeToBinaryBit(Long tenantId, Integer resourceType, String operationCode) {
          Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
          return opMap.values().stream()
              .filter(op -> Objects.equals(op.getCode(), operationCode))
              .map(OperationPermission::getBinaryBit)
              .findFirst().orElse(null);
      }
  }
  ```
- **MIRROR**: SERVICE_PATTERN
- **IMPORTS**: `@Service, OperationPermissionMapper, CacheService, PermCacheCatalog`
- **GOTCHA**: 缓存 Key 格式 "op_perm:" + resourceType，不包含 tenantId（缓存在 tenant namespace 下）
- **VALIDATE**: 编译成功，单元测试覆盖核心方法

### Task 5: 新增 RoleResourcePermissionMapper 位操作方法
- **ACTION**: 新增位操作查询方法
- **IMPLEMENT**:
  ```java
  /**
   * 按位掩码查询类型级权限（scopeAll=true）
   * <p>
   * PostgreSQL 位操作：WHERE granted_bits & bit_mask != 0
   * 返回所有可能覆盖目标操作的权限条目。
   * </p>
   */
  List<RoleResourcePermission> selectScopeAllPermsByBits(
      @Param("tenantId") Long tenantId,
      @Param("roleIds") Set<Long> roleIds,
      @Param("resourceTypes") Set<Integer> resourceTypes,
      @Param("bitMask") Long bitMask);
  
  /**
   * 按位掩码查询实例级权限
   */
  List<RoleResourcePermission> selectInstancePermsByBits(
      @Param("tenantId") Long tenantId,
      @Param("roleIds") Set<Long> roleIds,
      @Param("resourceEntityIds") Set<Long> resourceEntityIds,
      @Param("bitMask") Long bitMask);
  ```
- **MIRROR**: BIT_OPERATION_QUERY_PATTERN
- **IMPORTS**: 无新增
- **GOTCHA**: bitMask 是覆盖集 OR 值，不是单个 binaryBit
- **VALIDATE**: 编译成功

### Task 6: 实现 RoleResourcePermissionMapper.xml 位操作 SQL
- **ACTION**: 新增位操作 SQL 实现
- **IMPLEMENT**:
  ```xml
  <select id="selectScopeAllPermsByBits" 
          resultType="cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission">
      SELECT id, tenant_id, abstract_role_id, resource_entity_id, resource_type,
             granted_bits, condition_id, depend_on, can_grant, grant_source, ...
      FROM role_resource_permission
      WHERE tenant_id = #{tenantId}
        AND abstract_role_id IN 
        <foreach collection="roleIds" item="rid" open="(" separator="," close=")">#{rid}</foreach>
        AND scope_all = true
        AND delete_flag = 0
        <if test="resourceTypes != null and !resourceTypes.isEmpty()">
        AND resource_type IN
        <foreach collection="resourceTypes" item="rt" open="(" separator="," close=")">#{rt}</foreach>
        </if>
        <if test="bitMask != null and bitMask != 0">
        AND (granted_bits & #{bitMask}) != 0
        </if>
  </select>
  
  <select id="selectInstancePermsByBits" ...>
      ... WHERE ... AND (granted_bits & #{bitMask}) != 0
  </select>
  ```
- **MIRROR**: RoleResourcePermissionMapper.xml 现有 selectScopeAllPerms
- **IMPORTS**: 无
- **GOTCHA**: PostgreSQL 位操作语法：`&` 是位与，`!= 0` 表示有交集
- **VALIDATE**: SQL 语法正确，可手动测试

### Task 7: 更新 RolePermEntryMapper
- **ACTION**: 适配 granted_bits 字段
- **IMPLEMENT**:
  ```java
  public RolePermEntry toEntry(RoleResourcePermission perm) {
      return new RolePermEntry(
          perm.getId(),
          perm.getAbstractRoleId(),
          perm.getResourceEntityId(),
          null,  // resourceCode 稍后填充
          perm.getResourceType(),
          perm.getGrantedBits(),  // ← 改为 grantedBits
          null,  // operationCode 稍后填充
          null,  // effectiveBits 稀后填充
          perm.getGrantSource(),
          perm.getCanGrant(),
          perm.getConditionId(),
          perm.getConditionId() != null,
          perm.getDependOn()
      );
  }
  
  /**
   * 批量填充操作信息（从 OperationPermission）
   */
  public List<RolePermEntry> fillOperationInfo(
      List<RolePermEntry> entries,
      Map<Long, OperationPermission> opMap) {
      return entries.stream().map(e -> {
          OperationPermission op = opMap.values().stream()
              .filter(o -> Objects.equals(o.getBinaryBit(), e.grantedBits()))
              .findFirst().orElse(null);
          return new RolePermEntry(
              e.permissionId(), e.roleId(), e.resourceEntityId(),
              e.resourceCode(), e.resourceType(),
              e.grantedBits(),
              op != null ? op.getCode() : null,
              op != null ? op.getEffectiveBits() : null,
              e.grantSource(), e.canGrant(), e.conditionId(),
              e.hasCondition(), e.dependOn()
          );
      }).toList();
  }
  ```
- **MIRROR**: RolePermEntryMapper 现有方法
- **IMPORTS**: 无新增
- **GOTCHA**: operationCode/effectiveBits 从 OperationPermission 反查
- **VALIDATE**: 编译成功

### Task 8: 重构 PermQueryEngine.query()
- **ACTION**: 使用位操作查询替代 ID 查询
- **IMPLEMENT**:
  ```java
  public PermResult query(PermQuery q) {
      ResolveContext ctx = new ResolveContext(q.tenantId(), typeResolutionService);
      
      // 1. 解析角色
      Set<Long> roleIds = resolveRoleIds(q);
      
      // 2. 解析资源类型值
      Set<Integer> resourceTypes = resolveResourceTypes(q, ctx);
      
      // 3. 预加载 OperationPermission（核心变更）
      Map<Integer, OperationPermissionCacheService> opCacheByType = new HashMap<>();
      for (Integer resourceType : resourceTypes) {
          opCacheByType.put(resourceType, 
              operationPermissionCacheService.loadByResourceType(q.tenantId(), resourceType));
      }
      
      // 4. 解析目标操作 binaryBit（核心变更）
      Set<Long> targetBinaryBits = resolveBinaryBits(q, opCacheByType);
      
      // 5. 计算覆盖位掩码（核心变更）
      long targetBitMask = computeTargetBitMask(opCacheByType, targetBinaryBits);
      
      // 6. 位操作查询类型级权限（核心变更）
      List<RolePermEntry> scopeAllEntries = queryScopeAllByBits(
          q.tenantId(), roleIds, resourceTypes, targetBitMask);
      
      // 7. 填充操作信息（核心变更）
      scopeAllEntries = fillOperationInfo(scopeAllEntries, opCacheByType);
      
      // 8. 精确过滤（使用 inheritMask 动态计算）
      scopeAllEntries = filterByEffectiveBits(scopeAllEntries, targetBinaryBits);
      
      // ... 后续处理（条件评估、冲突解决等）
  }
  
  private Set<Long> resolveBinaryBits(PermQuery q, 
      Map<Integer, Map<Long, OperationPermission>> opCacheByType) {
      Set<Long> result = new HashSet<>();
      for (String rtCode : q.resourceTypeCodes()) {
          Integer resourceType = ctx.getResourceTypeValue(rtCode);
          if (resourceType == null) continue;
          for (String opCode : q.operationCodes()) {
              Long binaryBit = operationPermissionCacheService.codeToBinaryBit(
                  q.tenantId(), resourceType, opCode);
              if (binaryBit != null) result.add(binaryBit);
          }
      }
      return result;
  }
  
  private long computeTargetBitMask(
      Map<Integer, Map<Long, OperationPermission>> opCacheByType, Set<Long> targetBinaryBits) {
      long mask = 0L;
      for (Long targetBit : targetBinaryBits) {
          for (Map<Long, OperationPermission> opMap : opCacheByType.values()) {
              for (OperationPermission op : opMap.values()) {
                  if ((op.getEffectiveBits() & targetBit) != 0) {
                      mask |= op.getBinaryBit();
                  }
              }
          }
      }
      return mask;
  }
  
  private List<RolePermEntry> filterByEffectiveBits(
      List<RolePermEntry> entries, Set<Long> targetBinaryBits) {
      List<RolePermEntry> result = new ArrayList<>();
      for (RolePermEntry e : entries) {
          Long effectiveBits = e.effectiveBits();
          if (effectiveBits == null) continue;
          for (Long targetBit : targetBinaryBits) {
              if ((effectiveBits & targetBit) != 0) {
                  result.add(e);
                  break;
              }
          }
      }
      return result;
  }
  ```
- **MIRROR**: PermQueryEngine 现有 query() 流程
- **IMPORTS**: `OperationPermissionCacheService`
- **GOTCHA**: 
  - 位掩码查询返回所有可能覆盖的条目，需要精确过滤
  - inheritMask 动态计算，不固化在 granted_bits
- **VALIDATE**: 单元测试验证 inheritMask 语义

### Task 9: 重构 PermQueryEngine.getDeniedIds()
- **ACTION**: 使用位操作查询替代 ID 查询
- **IMPLEMENT**: 同 Task 8 流程，但单资源类型单操作
- **MIRROR**: getDeniedIds 现有结构
- **IMPORTS**: 无新增
- **GOTCHA**: 单操作场景，bitMask 计算 simpler
- **VALIDATE**: 现有测试兼容

### Task 10: 更新 ResolveContext
- **ACTION**: 新增 binaryBit 相关方法
- **IMPLEMENT**:
  ```java
  /**
   * 批量预解析操作 binaryBit
   */
  public void prepareBinaryBits(Set<String> resourceTypeCodes, Set<String> operationCodes,
      OperationPermissionCacheService opCacheService) {
      // 按 resourceTypeCode 预加载
      for (String rtCode : resourceTypeCodes) {
          Integer resourceType = getResourceTypeValue(rtCode);
          if (resourceType == null) continue;
          opCacheService.loadByResourceType(tenantId, resourceType);
      }
  }
  
  /**
   * 获取操作的 binaryBit
   */
  public Long getBinaryBit(String resourceTypeCode, String operationCode,
      OperationPermissionCacheService opCacheService) {
      Integer resourceType = getResourceTypeValue(resourceTypeCode);
      if (resourceType == null) return null;
      return opCacheService.codeToBinaryBit(tenantId, resourceType, operationCode);
  }
  ```
- **MIRROR**: ResolveContext 现有 prepareOperations
- **IMPORTS**: `OperationPermissionCacheService`
- **GOTCHA**: 需注入 opCacheService
- **VALIDATE**: 编译成功

### Task 11: 创建 inheritMask 语义测试
- **ACTION**: 验证 MANAGE 包含 VIEW 权限语义
- **IMPLEMENT**:
  ```java
  @Test
  void testInheritMask_ManageImpliesView() {
      // 设置数据
      OperationPermission view = new OperationPermission();
      view.setId(1L); view.setBinaryBit(1L); view.setInheritMask(0L);
      
      OperationPermission manage = new OperationPermission();
      manage.setId(4L); manage.setBinaryBit(8L); manage.setInheritMask(7L); // VIEW|CREATE|EDIT
      
      // 授予 MANAGE 权限
      RoleResourcePermission perm = new RoleResourcePermission();
      perm.setGrantedBits(8L); // MANAGE.binaryBit
      
      // 校验 VIEW 权限
      // targetBinaryBit = 1
      // coveringBits = {1, 2, 4, 8} (所有 effectiveBits & 1 != 0)
      // bitMask = 15
      // 查询：WHERE granted_bits & 15 != 0 → 返回 perm
      // 精确过滤：manage.effectiveBits(15) & 1 = 1 → 通过
      
      assertTrue(result.allowed());
  }
  ```
- **MIRROR**: PermQueryEngineTest 现有测试结构
- **IMPORTS**: 无新增
- **GOTCHA**: 测试数据需手动构造 binaryBit/inheritMask
- **VALIDATE**: 测试通过

### Task 12: 创建 OperationPermissionCacheService 测试
- **ACTION**: 验证缓存服务核心方法
- **IMPLEMENT**:
  ```java
  @Test
  void testComputeCoveringBits() {
      // 设置：VIEW(1), CREATE(2), EDIT(4), MANAGE(8|7)
      // targetBinaryBit = 1 (VIEW)
      // expected coveringBits = {1, 8} (VIEW 和 MANAGE)
  }
  
  @Test
  void testComputeTargetBitMask() {
      // coveringBits = {1, 8}
      // expected bitMask = 9
  }
  ```
- **MIRROR**: Service 测试模式
- **IMPORTS**: 无新增
- **GOTCHA**: Mock OperationPermissionMapper
- **VALIDATE**: 测试通过

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| testInheritMask_ManageImpliesView | granted_bits=8, target=VIEW(1) | allowed=true | inheritMask 语义 |
| testComputeCoveringBits | VIEW(1), MANAGE(8|7) | coveringBits={1,8} | 位运算正确性 |
| testBitOperationQuery | bitMask=15, granted_bits=8 | 匹配 | PostgreSQL 位操作 |
| testResolveBinaryBits | operationCode="VIEW" | binaryBit=1 | 码转位值 |
| testFilterByEffectiveBits | granted(15), target(1) | 通过 | 精确过滤 |

### Edge Cases Checklist
- [ ] granted_bits = 0（空权限）
- [ ] targetBinaryBit = null（无目标操作）
- [ ] inheritMask = 0（无继承）
- [ ] 多资源类型混合查询
- [ ] 同一 resourceType 多目标操作

---

## Validation Commands

### Static Analysis
```bash
mvn compile -pl permission-center,perm-entity -am -q
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
mvn test -pl permission-center -Dtest=PermQueryEngineTest -q
```
EXPECT: All tests pass

### Full Test Suite
```bash
mvn test -pl permission-center -q
```
EXPECT: No regressions

### Manual Validation
- [ ] 运行 inheritMask 语义测试验证
- [ ] 验证位操作查询 SQL 正确性
- [ ] 验证缓存服务加载/失效逻辑

---

## Acceptance Criteria
- [ ] RoleResourcePermission.granted_bits 字段存在
- [ ] RolePermEntry.grantedBits 替代 operationPermissionId
- [ ] OperationPermissionCacheService 创建完成
- [ ] 位操作查询方法实现
- [ ] PermQueryEngine 重构完成
- [ ] inheritMask 语义测试通过
- [ ] 编译零错误
- [ ] 现有测试无回归

## Completion Checklist
- [ ] Entity 修改符合现有模式
- [ ] Mapper 方法命名符合约定
- [ ] SQL 位操作语法正确
- [ ] 缓存条目配置合理
- [ ] 测试覆盖核心逻辑
- [ ] 无多余代码添加

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 位操作 SQL 性能 | Low | Medium | PostgreSQL 位操作高效，实测验证 |
| inheritMask 变更缓存失效 | Medium | Low | 缓存60分钟，操作变更时手动失效 |
| granted_bits 空值 | Low | Low | 默认值0，过滤条件检查 |

## Notes
- 项目未上线，无需历史数据迁移
- inheritMask 动态计算，不固化存储
- granted_bits 存 binaryBit（单个位值），一条记录对应一个操作授予

---

> **Confidence Score**: 8/10 — 方案清晰，模式明确，但位操作查询需验证 SQL 性能