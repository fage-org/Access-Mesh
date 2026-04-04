# 核心权限接口桥接架构设计

本文档定义权限管理核心接口的架构设计，采用桥接模式将高层操作与资源类型维度的子操作实现解耦。与 `DESIGN.md`（表结构与流程设计）和 `MIXED_KERNEL_ARCHITECTURE.md`（三服务拓扑）配套使用。

---

## 1. 桥接模式总体结构

两个独立变化维度：

- **操作维度**（Abstraction）：鉴权 check / 授权 grant / 回收 revoke / 快照 snapshot
- **资源类型维度**（Implementor）：MENU / API / DATA / 自定义...

每个高层操作内部按 Template Method 定义步骤序列，每一步通过 Bridge 引用委托给当前资源类型的子操作实现。新增操作不影响资源类型 Handler，新增资源类型不影响操作流程。

```
PermissionService (Bridge Abstraction - 高层操作入口)
    │
    │ uses
    ▼
ResourceTypeHandlerRegistry (按 resource_type 分发)
    │
    │ getHandler(resourceType)
    ▼
ResourceTypeHandler (Bridge Implementor - 组合 7 个子操作)
    │
    ├── getPermissionMatcher()     → PermissionMatcher
    ├── getInheritanceExpander()   → InheritanceExpander
    ├── getConditionEvaluator()    → ConditionEvaluator
    ├── getConflictDetector()      → ConflictDetector
    ├── getDependencyChecker()     → DependencyChecker
    ├── getGrantValidator()        → GrantValidator
    └── getSnapshotAssembler()     → SnapshotAssembler

继承关系：
ResourceTypeHandler (interface)
    │
    ├── DefaultResourceTypeHandler (通用默认实现，getResourceType()=null)
    │       │
    │       ├── MenuResourceTypeHandler  (覆盖 InheritanceExpander)
    │       ├── ApiResourceTypeHandler   (覆盖 PermissionMatcher + SnapshotAssembler)
    │       └── DataResourceTypeHandler  (覆盖 InheritanceExpander + SnapshotAssembler)
    │
    └── ... (新增资源类型只需新增一个 @Component 类)
```

---

## 2. 七个子操作接口

子操作是桥接实现层的最小粒度单位。每个接口有独立的 Default 实现，ResourceTypeHandler 通过组合模式持有它们。

| 子操作 | 接口名 | 职责 | 典型变化场景 |
|--------|--------|------|-------------|
| 权限匹配 | PermissionMatcher | 从 role_resource_permission 中查找匹配记录 | API 可能需路径前缀匹配，MENU 精确匹配 |
| 资源继承展开 | InheritanceExpander | 按 inherit_mode 展开资源树 | MENU 树形遍历、API 扁平/前缀、DATA 组织层级 |
| 条件评估 | ConditionEvaluator | 评估 permission_condition 是否满足 | 不同资源类型可注入不同上下文变量 |
| 冲突检测 | ConflictDetector | 检查 permission_conflict_rule 互斥 | 按 resource_type_value 过滤规则集 |
| 依赖校验 | DependencyChecker | 校验 resource_dependency 完整性 | 不同资源类型的依赖链语义不同 |
| 授权校验 | GrantValidator | 授权写入前的业务校验 | API 需校验路由映射存在、DATA 需校验数据源 |
| 快照组装 | SnapshotAssembler | 将授权记录转换为消费端快照格式 | API->route 映射、MENU->树结构、DATA->范围描述 |

### 2.1 共享上下文对象

```java
/**
 * 权限流水线共享上下文，流经所有子操作步骤。
 * 入参在构造时不可变，中间结果在流水线执行过程中逐步填充。
 */
public class PermissionContext {
    // ---- 入参（不可变）----
    private final Long tenantId;
    private final Long userId;
    private final Long bizDomainId;                       // 可选，限定域
    private final InheritMode inheritMode;                // NONE/CHILDREN/PARENT/BOTH
    private final Map<String, Object> evalContext;        // 条件评估变量

    // ---- 流水线中逐步填充 ----
    private List<ResolvedRole> roles;                     // Step 1 填充
    private ResourceEntity resource;                      // Step 2 填充
    private OperationPermission operation;                // Step 2 填充
    private Set<Long> expandedResourceIds;                // Step 4 填充
    private List<MatchedPermission> matchedPermissions;   // Step 5 填充
    private List<ConflictDetail> detectedConflicts;       // Step 8 填充
}

public enum InheritMode {
    NONE,       // 只匹配精确资源
    CHILDREN,   // 向下展开子资源
    PARENT,     // 向上检查父资源授权
    BOTH        // 双向检查
}

public enum DenyReason {
    NO_ROLE,           // 用户在指定域下无角色
    NO_PERMISSION,     // 无匹配的授权记录
    CONDITION_FAIL,    // 条件评估不通过
    CONFLICT,          // 冲突互斥导致失效
    DEPENDENCY_FAIL    // 依赖链不完整
}
```

### 2.2 子操作接口定义

```java
/**
 * 权限匹配：从授权记录中查找与当前请求匹配的权限。
 * 默认实现为标准 SQL 查 role_resource_permission。
 * API 类型可扩展为路径前缀匹配。
 */
public interface PermissionMatcher {
    List<MatchedPermission> match(
        Set<Long> roleIds,
        Set<Long> resourceIds,           // 含继承展开后的 id 集合
        Long operationPermissionId,
        PermissionContext ctx
    );
}

/**
 * 资源继承展开：按 inheritMode 展开资源 id 集合（含自身）。
 * CHILDREN 向下展开子资源，PARENT 向上检查父资源。
 * 默认实现沿 resource_entity.path 做 LIKE 查询。
 * MENU 可定制树形遍历，DATA 可定制组织层级展开。
 */
public interface InheritanceExpander {
    Set<Long> expand(Long resourceEntityId, InheritMode mode, PermissionContext ctx);
}

/**
 * 条件评估：评估单条授权的生效条件。
 * condition 为 null 时返回 true。
 * 默认实现：PRESET 走 handler 编码分发，CUSTOM 走表达式引擎。
 */
public interface ConditionEvaluator {
    boolean evaluate(PermissionCondition condition, PermissionContext ctx);
}

/**
 * 冲突检测：从已匹配权限中检测互斥冲突，返回应失效的操作对。
 * 默认实现查 permission_conflict_rule，按 resource_type_value 过滤。
 */
public interface ConflictDetector {
    List<ConflictDetail> detect(
        Long userId,
        Set<Long> resourceIds,
        Set<Long> grantedOperationIds,
        PermissionContext ctx
    );
}

/**
 * 依赖校验：校验主体资源 + 操作所需的依赖链是否满足。
 * 默认实现查 resource_dependency，递归校验用户对依赖资源是否有 required_operation。
 */
public interface DependencyChecker {
    DependencyCheckResult check(
        Long userId,
        Long resourceEntityId,
        Long operationPermissionId,
        PermissionContext ctx
    );
}

/**
 * 授权校验：授权写入前的业务校验。
 * 资源-操作匹配、域约束等由通用层完成，此处做资源类型专属校验。
 * API 类型可校验路由映射是否存在，DATA 类型可校验数据源是否有效。
 */
public interface GrantValidator {
    ValidationResult validate(GrantPermissionRequest request, PermissionContext ctx);
}

/**
 * 快照组装：将一批已授权记录转为消费端可用的快照条目。
 * API 类型映射到 service_code + http_method + path_pattern。
 * MENU 类型映射到菜单树结构。
 * DATA 类型映射到数据范围描述。
 */
public interface SnapshotAssembler {
    List<SnapshotEntry> assemble(
        Long userId,
        List<MatchedPermission> permissions,
        PermissionContext ctx
    );
}
```

### 2.3 Default 实现要点

每个子操作的 Default 实现覆盖最通用逻辑（适用大部分资源类型）：

- **DefaultPermissionMatcher**：标准 SQL 查 `role_resource_permission WHERE abstract_role_id IN (?) AND resource_entity_id IN (?) AND operation_permission_id = ?`
- **DefaultInheritanceExpander**：NONE 返回 {自身}；CHILDREN 沿 path LIKE 查子；PARENT 沿 path LIKE 向上查父
- **DefaultConditionEvaluator**：PRESET 走 handler 编码分发（Spring Bean name 约定），CUSTOM 走表达式引擎（传入 ctx.evalContext）
- **DefaultConflictDetector**：查 permission_conflict_rule，按 resource_type_value 过滤，OR 合并 grantedOperationIds 后检测互斥位
- **DefaultDependencyChecker**：查 resource_dependency，递归校验用户对依赖资源是否有 required_operation
- **DefaultGrantValidator**：校验 operation_permission.resource_type 与 resource_entity.resource_type 匹配
- **DefaultSnapshotAssembler**：输出 (resource_code, operation_code, condition_id) 列表；首期接口快照仅下发无条件授权，`condition_id != null` 的授权保留在精确鉴权链路

---

## 3. ResourceTypeHandler 组合接口

```java
/**
 * 资源类型处理器（Bridge Implementor）。
 * 通过组合 7 个子操作接口实现资源类型维度的行为定制。
 * 所有子操作方法均有 default 实现，资源类型 Handler 只需覆盖需要定制的部分。
 */
public interface ResourceTypeHandler {

    /**
     * 返回所处理的 resource_type 枚举值（来自 type_definition.type_key='resource_type'）。
     * 返回 null 表示默认处理器，匹配所有未注册专用 Handler 的资源类型。
     */
    Integer getResourceType();

    default PermissionMatcher getPermissionMatcher() {
        return DefaultPermissionMatcher.INSTANCE;
    }

    default InheritanceExpander getInheritanceExpander() {
        return DefaultInheritanceExpander.INSTANCE;
    }

    default ConditionEvaluator getConditionEvaluator() {
        return DefaultConditionEvaluator.INSTANCE;
    }

    default ConflictDetector getConflictDetector() {
        return DefaultConflictDetector.INSTANCE;
    }

    default DependencyChecker getDependencyChecker() {
        return DefaultDependencyChecker.INSTANCE;
    }

    default GrantValidator getGrantValidator() {
        return DefaultGrantValidator.INSTANCE;
    }

    default SnapshotAssembler getSnapshotAssembler() {
        return DefaultSnapshotAssembler.INSTANCE;
    }
}
```

资源类型专用 Handler 只需 override 需要定制的子操作，其余继承 default：

```java
@Component
public class ApiResourceTypeHandler implements ResourceTypeHandler {
    private final ApiPermissionMatcher apiMatcher;
    private final ApiSnapshotAssembler apiAssembler;

    @Override
    public Integer getResourceType() { return ResourceTypes.API; }

    // 仅覆盖两个子操作，其余 5 个走 default
    @Override
    public PermissionMatcher getPermissionMatcher() { return apiMatcher; }

    @Override
    public SnapshotAssembler getSnapshotAssembler() { return apiAssembler; }
}
```

---

## 4. Registry 与分发

```java
/**
 * 资源类型处理器注册中心。
 * Spring 自动发现所有 ResourceTypeHandler 实现，按 resourceType 注册。
 * 新增资源类型只需新增一个 @Component 类，无需修改注册逻辑。
 */
@Component
public class ResourceTypeHandlerRegistry {
    private final Map<Integer, ResourceTypeHandler> handlers;
    private final ResourceTypeHandler defaultHandler;

    public ResourceTypeHandlerRegistry(List<ResourceTypeHandler> allHandlers) {
        this.defaultHandler = allHandlers.stream()
            .filter(h -> h.getResourceType() == null)
            .findFirst()
            .orElseGet(DefaultResourceTypeHandler::new);

        this.handlers = allHandlers.stream()
            .filter(h -> h.getResourceType() != null)
            .collect(Collectors.toMap(ResourceTypeHandler::getResourceType, h -> h));
    }

    /**
     * 按 resource_type 获取 Handler。
     * resourceType 为 null 或未注册时返回默认 Handler。
     */
    public ResourceTypeHandler getHandler(Integer resourceType) {
        if (resourceType == null) return defaultHandler;
        return handlers.getOrDefault(resourceType, defaultHandler);
    }
}
```

---

## 5. PermissionService 流水线设计

### 5.1 通用服务层

PermissionService 依赖以下通用服务，所有资源类型共享：

| 服务 | 职责 |
|------|------|
| RoleResolverService | user_role JOIN abstract_role，按 biz_domain_id 和 valid_from/valid_to 过滤 |
| OperationInheritanceService | binary_bit \| inherit_mask 位运算，计算 effective mask |
| DomainScopeValidator | domain_scope_config + domain_relation_config 校验 |
| ChangeLogService | 写 permission_change_log |
| PermissionVersionService | 递增 permission_version |

### 5.2 鉴权流水线 (check)

```
PermissionService.check(request)
    │
    ├── 1. resolveRoles (通用 - RoleResolverService)
    │      查 user_role + abstract_role，按域和时间过滤
    │      若无角色 → deny(NO_ROLE)
    │
    ├── 2. loadResource & loadOperation (通用)
    │      加载 resource_entity 和 operation_permission
    │
    ├── 3. registry.getHandler(resource.resourceType) (桥接分发)
    │
    ├── 4. handler.inheritanceExpander.expand() (子操作)
    │      按 inherit_mode 展开资源 id 集合
    │
    ├── 5. handler.permissionMatcher.match() (子操作)
    │      查找匹配的授权记录
    │      若无匹配 → deny(NO_PERMISSION)
    │
    ├── 6. computeOperationInheritance (通用位运算)
    │      binary_bit | inherit_mask 过滤
    │
    ├── 7. handler.conditionEvaluator.evaluate() for each (子操作)
    │      逐条评估条件，过滤不满足的授权
    │      若全部不满足 → deny(CONDITION_FAIL)
    │
    ├── 8. handler.conflictDetector.detect() (子操作)
    │      检测互斥冲突，移除冲突的操作对
    │      若全部被冲突 → deny(CONFLICT)
    │
    ├── 9. handler.dependencyChecker.check() (子操作，可选)
    │      由 request.checkDependency 控制是否启用
    │      若依赖不满足 → deny(DEPENDENCY_FAIL, gaps)
    │
    └── 10. assembleResult (通用)
           汇总：至少一条通过 → granted
```

```java
public class PermissionService {
    private final ResourceTypeHandlerRegistry registry;
    private final RoleResolverService roleResolver;
    private final OperationInheritanceService opInherit;
    private final ChangeLogService changeLogService;
    private final PermissionVersionService versionService;

    public PermissionCheckResult check(PermissionCheckRequest request) {
        PermissionContext ctx = buildContext(request);

        // 1. 通用：解析用户角色
        ctx.setRoles(roleResolver.resolve(ctx.getTenantId(), ctx.getUserId(), ctx.getBizDomainId()));
        if (ctx.getRoles().isEmpty()) return deny(NO_ROLE);

        // 2. 通用：加载资源和操作
        ctx.setResource(loadResource(request.getResourceEntityId()));
        ctx.setOperation(loadOperation(request.getOperationPermissionId()));

        // 3. 桥接分发：按资源类型获取 handler
        ResourceTypeHandler handler = registry.getHandler(ctx.getResource().getResourceType());

        // 4. 子操作：资源继承展开
        Set<Long> resourceIds = handler.getInheritanceExpander()
            .expand(request.getResourceEntityId(), request.getInheritMode(), ctx);
        ctx.setExpandedResourceIds(resourceIds);

        // 5. 子操作：权限匹配
        Set<Long> roleIds = ctx.getRoles().stream().map(ResolvedRole::getId).collect(toSet());
        List<MatchedPermission> matched = handler.getPermissionMatcher()
            .match(roleIds, resourceIds, request.getOperationPermissionId(), ctx);
        if (matched.isEmpty()) return deny(NO_PERMISSION);

        // 6. 通用：操作继承位运算过滤
        matched = opInherit.filterByInheritance(matched, ctx.getOperation());

        // 7. 子操作：条件评估（逐条过滤）
        ConditionEvaluator evaluator = handler.getConditionEvaluator();
        matched = matched.stream()
            .filter(m -> evaluator.evaluate(m.getCondition(), ctx))
            .collect(toList());
        if (matched.isEmpty()) return deny(CONDITION_FAIL);

        // 8. 子操作：冲突检测
        Set<Long> grantedOpIds = matched.stream()
            .map(MatchedPermission::getOperationId).collect(toSet());
        List<ConflictDetail> conflicts = handler.getConflictDetector()
            .detect(ctx.getUserId(), resourceIds, grantedOpIds, ctx);
        matched = removeConflicted(matched, conflicts);
        if (matched.isEmpty()) return deny(CONFLICT);

        // 9. 子操作：依赖校验（可选）
        if (request.isCheckDependency()) {
            DependencyCheckResult depResult = handler.getDependencyChecker()
                .check(ctx.getUserId(), request.getResourceEntityId(),
                       request.getOperationPermissionId(), ctx);
            if (!depResult.isSatisfied()) return deny(DEPENDENCY_FAIL, depResult.getGaps());
        }

        // 10. 通用：汇总
        return granted(matched, conflicts);
    }
}
```

### 5.3 授权流水线 (grant)

```
PermissionService.grant(request)
    │
    ├── 1. validateDomainScope (通用 - DomainScopeValidator)
    │      domain_scope_config + domain_relation_config
    │
    ├── 2. validateOperationResourceTypeMatch (通用)
    │      operation_permission.resource_type vs resource_entity.resource_type
    │
    ├── 3. handler.grantValidator.validate() (子操作)
    │      资源类型专属校验
    │
    ├── 4. writePermission (通用)
    │      INSERT role_resource_permission
    │
    └── 5. writeChangeLog + incrementVersion (通用)
           permission_change_log + permission_version
```

```java
public GrantResult grant(GrantPermissionRequest request) {
    PermissionContext ctx = buildContext(request);
    ResourceTypeHandler handler = registry.getHandler(loadResourceType(request));

    // 1. 通用：域范围校验
    validateDomainScope(request, ctx);

    // 2. 通用：操作-资源类型匹配校验
    validateOperationResourceTypeMatch(request);

    // 3. 子操作：资源类型专属授权校验
    ValidationResult vr = handler.getGrantValidator().validate(request, ctx);
    if (!vr.isValid()) return GrantResult.rejected(vr.getReasons());

    // 4. 通用：写入 role_resource_permission
    Long id = writePermission(request);

    // 5. 通用：写变更日志 + 递增版本
    changeLogService.log(request, id);
    versionService.increment(ctx.getTenantId(), "role_resource_permission", id);

    return GrantResult.success(id);
}
```

### 5.4 快照组装流水线 (buildSnapshot)

快照组装按资源类型分组，每组使用对应 Handler 的子操作。

```
PermissionService.buildSnapshot(request)
    │
    ├── 1. resolveRoles (通用)
    │
    ├── 2. queryAllPermissions + groupByResourceType (通用)
    │
    ├── 3. for each resourceType:
    │      ├── handler.conflictDetector.detect() (子操作)
    │      │      冲突权限从快照排除，触发异步通知
    │      │
    │      └── handler.snapshotAssembler.assemble() (子操作)
    │             每种资源类型输出各自格式的快照条目
    │
    └── 4. merge entries + attach version (通用)
```

```java
public PermissionSnapshot buildSnapshot(SnapshotRequest request) {
    PermissionContext ctx = buildContext(request);
    ctx.setRoles(roleResolver.resolve(ctx.getTenantId(), ctx.getUserId(), ctx.getBizDomainId()));

    // 按资源类型分组授权记录
    Map<Integer, List<MatchedPermission>> byType = groupByResourceType(ctx);

    List<SnapshotEntry> entries = new ArrayList<>();
    for (var entry : byType.entrySet()) {
        ResourceTypeHandler handler = registry.getHandler(entry.getKey());

        // 子操作：冲突检测（快照级）
        List<MatchedPermission> filtered = applyConflictFilter(handler, entry.getValue(), ctx);

        // 子操作：快照组装（首期接口快照仅处理无条件授权）
        entries.addAll(handler.getSnapshotAssembler()
            .assemble(ctx.getUserId(), filtered, ctx));
    }

    return new PermissionSnapshot(entries, versionService.current(ctx.getTenantId()));
}
```

---

## 6. 资源类型 Handler 扩展示例

### 6.1 API 资源类型：覆盖 PermissionMatcher + SnapshotAssembler

```java
@Component
public class ApiResourceTypeHandler implements ResourceTypeHandler {
    private final ApiPermissionMatcher apiMatcher;
    private final ApiSnapshotAssembler apiAssembler;

    @Override
    public Integer getResourceType() { return ResourceTypes.API; }

    @Override
    public PermissionMatcher getPermissionMatcher() { return apiMatcher; }

    @Override
    public SnapshotAssembler getSnapshotAssembler() { return apiAssembler; }
}
```

**ApiPermissionMatcher**：API 资源除标准 resource_entity_id 匹配外，还可通过 resource_api_mapping 做路径级匹配。

```java
@Component
public class ApiPermissionMatcher implements PermissionMatcher {
    @Override
    public List<MatchedPermission> match(Set<Long> roleIds, Set<Long> resourceIds,
                                          Long opId, PermissionContext ctx) {
        // 1. 先标准匹配 role_resource_permission
        List<MatchedPermission> result = defaultMatch(roleIds, resourceIds, opId);

        // 2. 如果请求带了 path 信息，额外通过 resource_api_mapping 做路径匹配
        if (ctx.getEvalContext().containsKey("requestPath")) {
            result.addAll(matchByApiRoute(roleIds, ctx));
        }
        return result;
    }
}
```

**ApiSnapshotAssembler**：API 快照需要携带 `service_code + http_method + path_pattern`，供 gateway 直接消费；首期仅组装无条件授权项，`condition_id != null` 的授权不进入接口快照。

```java
@Component
public class ApiSnapshotAssembler implements SnapshotAssembler {
    @Override
    public List<SnapshotEntry> assemble(Long userId, List<MatchedPermission> permissions,
                                         PermissionContext ctx) {
        // JOIN resource_api_mapping 输出 gateway 可直接消费的路由规则
        return permissions.stream()
            .flatMap(p -> loadApiMappings(p.getResourceId()).stream()
                .map(m -> SnapshotEntry.apiRoute(
                    m.getServiceCode(), m.getHttpMethod(),
                    m.getPathPattern(), p.getOperationCode())))
            .collect(toList());
    }
}
```

### 6.2 MENU 资源类型：覆盖 InheritanceExpander

```java
@Component
public class MenuResourceTypeHandler implements ResourceTypeHandler {
    private final MenuInheritanceExpander menuExpander;

    @Override
    public Integer getResourceType() { return ResourceTypes.MENU; }

    @Override
    public InheritanceExpander getInheritanceExpander() { return menuExpander; }
}
```

**MenuInheritanceExpander**：菜单树形继承，沿 path 做 LIKE 查询。

```java
@Component
public class MenuInheritanceExpander implements InheritanceExpander {
    @Override
    public Set<Long> expand(Long resourceEntityId, InheritMode mode, PermissionContext ctx) {
        if (mode == NONE) return Set.of(resourceEntityId);

        ResourceEntity resource = loadResource(resourceEntityId);
        Set<Long> result = new HashSet<>();
        result.add(resourceEntityId);

        if (mode == CHILDREN || mode == BOTH) {
            result.addAll(findDescendants(resource.getPath(), ctx));
        }
        if (mode == PARENT || mode == BOTH) {
            result.addAll(findAncestors(resource.getPath(), ctx));
        }
        return result;
    }
}
```

### 6.3 新增资源类型的步骤

只需三步：

1. 在 `type_definition` 注册新的 resource_type 枚举值
2. 创建 Handler 类实现 `ResourceTypeHandler`，只覆盖需要定制的子操作
3. 加 `@Component` 注解，Registry 自动发现

---

## 7. 通用 vs 桥接职责分界

| 环节 | 归属 | 说明 |
|------|------|------|
| 角色解析 | PermissionService 通用 | user_role JOIN abstract_role，按域/时间过滤 |
| 操作继承位运算 | PermissionService 通用 | binary_bit OR inherit_mask，所有类型一致 |
| 域范围/关系校验 | PermissionService 通用 | domain_scope_config + domain_relation_config |
| 写 role_resource_permission | PermissionService 通用 | 标准 INSERT |
| 写变更日志 + 递增版本 | PermissionService 通用 | permission_change_log + permission_version |
| 权限匹配 | ResourceTypeHandler 桥接 | 查询策略可因资源类型而异 |
| 资源继承展开 | ResourceTypeHandler 桥接 | 树形/扁平/层级 |
| 条件评估 | ResourceTypeHandler 桥接 | 可注入资源类型特有的上下文变量 |
| 冲突检测 | ResourceTypeHandler 桥接 | 按 resource_type_value 过滤规则集 |
| 依赖校验 | ResourceTypeHandler 桥接 | 依赖链语义因类型而异 |
| 授权校验 | ResourceTypeHandler 桥接 | 类型特有的前置校验 |
| 快照组装 | ResourceTypeHandler 桥接 | 输出格式因消费方而异 |

---

## 8. 请求与响应对象

### 8.1 鉴权

```java
public class PermissionCheckRequest {
    private Long tenantId;
    private Long userId;
    private Long resourceEntityId;
    private Long operationPermissionId;
    private Long bizDomainId;                       // 可选
    private InheritMode inheritMode;                // 默认 NONE
    private boolean checkDependency;                // 默认 false
    private Map<String, Object> context;            // 条件评估变量
}

public class PermissionCheckResult {
    private boolean granted;
    private DenyReason denyReason;                  // granted=false 时的原因
    private List<MatchedPermission> grantedBy;      // granted=true 时的授权来源
    private List<ConflictDetail> conflicts;         // 检测到的冲突（即使 granted=true 也可能有）
    private List<DependencyGap> dependencyGaps;     // checkDependency=true 时的不满足项
}
```

### 8.2 授权

```java
public class GrantPermissionRequest {
    private Long tenantId;
    private Long abstractRoleId;
    private Long resourceEntityId;
    private Long operationPermissionId;
    private boolean canManage;                      // 默认 false
    private Long conditionId;                       // 可选
    private String changeReason;                    // 变更原因
    private String requestId;                       // 追踪ID
}

public class GrantResult {
    private boolean success;
    private Long permissionId;                      // 成功时返回 role_resource_permission.id
    private List<String> rejectReasons;             // 失败时的原因列表
}
```

### 8.3 快照

```java
public class SnapshotRequest {
    private Long tenantId;
    private Long userId;
    private Long bizDomainId;                       // 可选
}

public class PermissionSnapshot {
    private List<SnapshotEntry> entries;
    private Long permissionVersion;
}

public class SnapshotEntry {
    private String resourceCode;
    private String operationCode;
    private Integer resourceType;
    // API 类型附加字段
    private String serviceCode;
    private String httpMethod;
    private String pathPattern;
    // 扩展字段
    private Map<String, Object> extra;
}
```

### 8.4 中间对象

```java
public class MatchedPermission {
    private Long roleResourcePermissionId;
    private Long roleId;
    private Long resourceId;
    private Long operationId;
    private String operationCode;
    private String resourceCode;
    private boolean canManage;
    private PermissionCondition condition;           // 可能为 null
}

public class ConflictDetail {
    private Long firstOperationId;
    private Long secondOperationId;
    private Long resourceId;
    private Long conflictRuleId;
}

public class DependencyCheckResult {
    private boolean satisfied;
    private List<DependencyGap> gaps;
}

public class DependencyGap {
    private Long dependsOnResourceId;
    private Long requiredOperationId;
    private String dependsOnResourceCode;
    private String requiredOperationCode;
}

public class ValidationResult {
    private boolean valid;
    private List<String> reasons;
}

public class ResolvedRole {
    private Long id;
    private Long bizDomainId;
    private Integer roleType;
    private String name;
}
```

---

## 9. 包结构

```
com.ruoyi.permission.core
├── service/                                    (高层服务 - Bridge Abstraction)
│   ├── PermissionService.java                  (鉴权/授权/回收/快照入口)
│   ├── RoleResolverService.java                (通用角色解析)
│   ├── OperationInheritanceService.java        (通用位运算)
│   ├── DomainScopeValidator.java               (通用域校验)
│   ├── ChangeLogService.java                   (通用变更日志)
│   └── PermissionVersionService.java           (通用版本管理)
├── handler/                                    (Bridge Implementor)
│   ├── ResourceTypeHandler.java                (桥接接口)
│   ├── ResourceTypeHandlerRegistry.java        (分发注册中心)
│   ├── DefaultResourceTypeHandler.java         (默认实现)
│   └── types/                                  (资源类型专用 Handler)
│       ├── MenuResourceTypeHandler.java
│       ├── ApiResourceTypeHandler.java
│       └── DataResourceTypeHandler.java
├── operation/                                  (7 个子操作接口)
│   ├── PermissionMatcher.java
│   ├── InheritanceExpander.java
│   ├── ConditionEvaluator.java
│   ├── ConflictDetector.java
│   ├── DependencyChecker.java
│   ├── GrantValidator.java
│   └── SnapshotAssembler.java
├── operation/defaults/                         (默认实现)
│   ├── DefaultPermissionMatcher.java
│   ├── DefaultInheritanceExpander.java
│   ├── DefaultConditionEvaluator.java
│   ├── DefaultConflictDetector.java
│   ├── DefaultDependencyChecker.java
│   ├── DefaultGrantValidator.java
│   └── DefaultSnapshotAssembler.java
└── context/                                    (上下文与 DTO)
    ├── PermissionContext.java
    ├── InheritMode.java
    ├── DenyReason.java
    ├── PermissionCheckRequest.java
    ├── PermissionCheckResult.java
    ├── GrantPermissionRequest.java
    ├── GrantResult.java
    ├── SnapshotRequest.java
    ├── PermissionSnapshot.java
    ├── SnapshotEntry.java
    ├── MatchedPermission.java
    ├── ConflictDetail.java
    ├── DependencyCheckResult.java
    ├── DependencyGap.java
    ├── ValidationResult.java
    └── ResolvedRole.java
```
