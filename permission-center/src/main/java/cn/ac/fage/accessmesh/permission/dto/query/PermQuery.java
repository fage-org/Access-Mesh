package cn.ac.fage.accessmesh.permission.dto.query;

import java.util.*;

/**
 * 统一权限查询输入类
 * <p>
 * 所有查询场景使用此单一DTO。
 * 调用者使用静态工厂方法创建；自定义字段可在创建后设置。
 * </p>
 */
public class PermQuery {

    // ── 查询主体 ──

    /**
     * 租户ID（必填）
     */
    private final Long tenantId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID集合（可选，用于角色级查询）
     */
    private Set<Long> roleIds;

    // ── 资源相关 ──

    /**
     * 资源类型编码集合
     */
    private Set<String> resourceTypeCodes;

    /**
     * 资源编码集合
     */
    private Set<String> resourceCodes;

    /**
     * 编码类型（CODE/ID等）
     */
    private String codeType;

    /**
     * 资源实体ID集合
     */
    private Set<Long> resourceEntityIds;

    /**
     * 继承模式（PARENT/CHILD/BOTH）
     */
    private String inheritMode;

    // ── 操作相关 ──

    /**
     * 操作编码集合
     */
    private Set<String> operationCodes;

    /**
     * 操作权限ID集合
     */
    private Set<Long> operationPermissionIds;

    // ── 查询行为 ──

    /**
     * 是否查询全范围权限
     */
    private boolean queryScopeAll = true;

    /**
     * 是否查询实例级权限
     */
    private boolean queryInstance = true;

    /**
     * 全范围匹配时是否提前返回
     */
    private boolean earlyReturnOnScopeAll = true;

    /**
     * 是否继承父资源权限
     */
    private boolean inheritParents;

    /**
     * 是否继承子资源权限
     */
    private boolean inheritChildren;

    /**
     * 是否评估条件
     */
    private boolean evaluateConditions = true;

    /**
     * 是否评估冲突规则
     */
    private boolean evaluateConflicts = true;

    /**
     * 是否评估位匹配
     */
    private boolean evaluateMatchesBit = true;

    /**
     * 是否为用户视图查询模式
     * <p>
     * 当 {@code true} 时，引擎跳过资源类型/操作ID/位掩码解析流程，
     * 直接查询该用户全部角色权限记录，不再按位过滤。
     * </p>
     */
    private boolean forUserView;

    // ── 返回内容 ──

    /**
     * 是否包含资源信息
     */
    private boolean includeResources;

    /**
     * 是否包含操作信息
     */
    private boolean includeOperations;

    /**
     * 是否包含角色信息
     */
    private boolean includeRoles;

    /**
     * 是否包含业务域信息
     */
    private boolean includeDomains;

    /**
     * 是否包含条件信息
     */
    private boolean includeConditions;

    // ── 缓存 ──

    /**
     * 是否使用角色缓存
     */
    private boolean useRoleCache = true;

    // ── 上下文 ──

    /**
     * 执行上下文（条件评估时使用）
     */
    private Map<String, Object> context;

    /**
     * 构造权限查询
     * <p>
     * 租户ID必填，其他字段通过工厂方法或setter设置。
     * </p>
     *
     * @param tenantId 租户ID
     */
    private PermQuery(Long tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    // ===== 工厂方法 =====

    /**
     * 创建单条权限校验查询
     * <p>
     * 类型+实例查询，全范围匹配时提前返回，完整评估，最小输出。
     * </p>
     *
     * @param tenantId         租户ID
     * @param userId           用户ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param operationCode    操作编码
     * @return 权限查询实例
     */
    public static PermQuery forAuthCheck(Long tenantId, Long userId,
                                          String resourceTypeCode, String resourceCode,
                                          String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCode == null ? Set.of() : Set.of(resourceTypeCode);
        q.resourceCodes = resourceCode == null ? null : Set.of(resourceCode);
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = true;
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.evaluateMatchesBit = true;
        return q;
    }

    
    /**
     * 创建接口权限校验查询
     * <p>
     * 类型优先，实例回退，完整评估，返回所有辅助信息。
     * </p>
     *
     * @param tenantId         租户ID
     * @param userId           用户ID
     * @param resourceTypeCodes 资源类型编码集合
     * @param resourceEntityIds 资源实体ID集合
     * @param operationCode    操作编码
     * @return 权限查询实例
     */
    public static PermQuery forInterfaceCheck(Long tenantId, Long userId,
                                               Set<String> resourceTypeCodes,
                                               Set<Long> resourceEntityIds,
                                               String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.resourceEntityIds = resourceEntityIds;
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = true;
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.evaluateMatchesBit = true;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        q.includeDomains = true;
        q.includeConditions = true;
        return q;
    }

    /**
     * 创建资源过滤查询
     * <p>
     * 仅实例级查询，不评估条件，包含资源和操作信息。
     * </p>
     *
     * @param tenantId         租户ID
     * @param userId           用户ID
     * @param resourceTypeCodes 资源类型编码集合
     * @param operationCodes   操作编码集合
     * @return 权限查询实例
     */
    public static PermQuery forResourceQuery(Long tenantId, Long userId,
                                              Set<String> resourceTypeCodes,
                                              Set<String> operationCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.operationCodes = operationCodes;
        q.queryScopeAll = false;
        q.queryInstance = true;
        q.evaluateConditions = false;
        q.evaluateConflicts = false;
        q.evaluateMatchesBit = true;
        q.includeResources = true;
        q.includeOperations = true;
        return q;
    }

    
    
    /**
     * 创建管理操作验证查询
     * <p>
     * 类型+实例查询，全范围匹配时提前返回，不评估，最小输出。
     * </p>
     *
     * @param tenantId         租户ID
     * @param operatorId       操作者ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param operationCode    操作编码
     * @return 权限查询实例
     */
    public static PermQuery forValidate(Long tenantId, Long operatorId,
                                         String resourceTypeCode, String resourceCode,
                                         String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = operatorId;
        q.resourceTypeCodes = resourceTypeCode == null ? Set.of() : Set.of(resourceTypeCode);
        q.resourceCodes = resourceCode == null ? null : Set.of(resourceCode);
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = true;
        q.evaluateConditions = false;
        q.evaluateConflicts = false;
        q.evaluateMatchesBit = false;
        return q;
    }

    
    /**
     * 创建范围查询
     * <p>
     * 类型+实例查询，无提前返回，不评估，不检查位匹配，返回所有辅助信息。
     * </p>
     *
     * @param tenantId         租户ID
     * @param userId           用户ID
     * @param resourceTypeCodes 资源类型编码集合
     * @param operationCodes   操作编码集合
     * @return 权限查询实例
     */
    public static PermQuery forScopeQuery(Long tenantId, Long userId,
                                           Set<String> resourceTypeCodes,
                                           Set<String> operationCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.operationCodes = operationCodes;
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = false;
        q.evaluateConditions = false;
        q.evaluateConflicts = false;
        q.evaluateMatchesBit = false;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        return q;
    }

    /**
     * 创建用户视图查询
     * <p>
     * 查询该用户的全部权限，不指定具体资源或操作。
     * 引擎跳过资源类型/操作ID/位掩码解析，直接用 {@code selectValidByRoleIds}
     * 查询全部角色权限记录，不按位过滤，返回完整数据。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 权限查询实例
     */
    public static PermQuery forUserView(Long tenantId, Long userId) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.forUserView = true;
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.earlyReturnOnScopeAll = false;
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.evaluateMatchesBit = false;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        return q;
    }

    // ===== Getter方法 =====

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public Long tenantId() { return tenantId; }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public Long userId() { return userId; }

    /**
     * 获取角色ID集合
     *
     * @return 角色ID集合
     */
    public Set<Long> roleIds() { return roleIds; }

    /**
     * 获取资源类型编码集合
     *
     * @return 资源类型编码集合
     */
    public Set<String> resourceTypeCodes() { return resourceTypeCodes; }

    /**
     * 获取资源编码集合
     *
     * @return 资源编码集合
     */
    public Set<String> resourceCodes() { return resourceCodes; }

    /**
     * 获取编码类型
     *
     * @return 编码类型
     */
    public String codeType() { return codeType; }

    /**
     * 获取资源实体ID集合
     *
     * @return 资源实体ID集合
     */
    public Set<Long> resourceEntityIds() { return resourceEntityIds; }

    /**
     * 获取继承模式
     *
     * @return 继承模式
     */
    public String inheritMode() { return inheritMode; }

    /**
     * 获取操作编码集合
     *
     * @return 操作编码集合
     */
    public Set<String> operationCodes() { return operationCodes; }

    /**
     * 获取操作权限ID集合
     *
     * @return 操作权限ID集合
     */
    public Set<Long> operationPermissionIds() { return operationPermissionIds; }

    /**
     * 获取是否查询全范围权限
     *
     * @return 是否查询全范围权限
     */
    public boolean queryScopeAll() { return queryScopeAll; }

    /**
     * 获取是否查询实例级权限
     *
     * @return 是否查询实例级权限
     */
    public boolean queryInstance() { return queryInstance; }

    /**
     * 获取全范围匹配时是否提前返回
     *
     * @return 是否提前返回
     */
    public boolean earlyReturnOnScopeAll() { return earlyReturnOnScopeAll; }

    /**
     * 获取是否继承父资源权限
     *
     * @return 是否继承父资源权限
     */
    public boolean inheritParents() { return inheritParents; }

    /**
     * 获取是否继承子资源权限
     *
     * @return 是否继承子资源权限
     */
    public boolean inheritChildren() { return inheritChildren; }

    /**
     * 获取是否评估条件
     *
     * @return 是否评估条件
     */
    public boolean evaluateConditions() { return evaluateConditions; }

    /**
     * 获取是否评估冲突规则
     *
     * @return 是否评估冲突规则
     */
    public boolean evaluateConflicts() { return evaluateConflicts; }

    /**
     * 获取是否评估位匹配
     *
     * @return 是否评估位匹配
     */
    public boolean evaluateMatchesBit() { return evaluateMatchesBit; }

    /**
     * 获取是否为用户视图查询模式
     *
     * @return 是否为用户视图查询模式
     */
    public boolean forUserView() { return forUserView; }

    /**
     * 获取是否包含资源信息
     *
     * @return 是否包含资源信息
     */
    public boolean includeResources() { return includeResources; }

    /**
     * 获取是否包含操作信息
     *
     * @return 是否包含操作信息
     */
    public boolean includeOperations() { return includeOperations; }

    /**
     * 获取是否包含角色信息
     *
     * @return 是否包含角色信息
     */
    public boolean includeRoles() { return includeRoles; }

    public boolean includeDomains() { return includeDomains; }

    public boolean includeConditions() { return includeConditions; }

    /**
     * 获取是否使用角色缓存
     *
     * @return 是否使用角色缓存
     */
    public boolean useRoleCache() { return useRoleCache; }

    /**
     * 获取执行上下文
     *
     * @return 执行上下文
     */
    public Map<String, Object> context() { return context; }

    // ===== Setter方法 =====

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public void setUserId(Long userId) { this.userId = userId; }

    /**
     * 设置角色ID集合
     *
     * @param roleIds 角色ID集合
     */
    public void setRoleIds(Set<Long> roleIds) { this.roleIds = roleIds; }

    /**
     * 设置资源类型编码集合
     *
     * @param resourceTypeCodes 资源类型编码集合
     */
    public void setResourceTypeCodes(Set<String> resourceTypeCodes) { this.resourceTypeCodes = resourceTypeCodes; }

    /**
     * 设置资源编码集合
     *
     * @param resourceCodes 资源编码集合
     */
    public void setResourceCodes(Set<String> resourceCodes) { this.resourceCodes = resourceCodes; }

    /**
     * 设置编码类型
     *
     * @param codeType 编码类型
     */
    public void setCodeType(String codeType) { this.codeType = codeType; }

    /**
     * 设置资源实体ID集合
     *
     * @param resourceEntityIds 资源实体ID集合
     */
    public void setResourceEntityIds(Set<Long> resourceEntityIds) { this.resourceEntityIds = resourceEntityIds; }

    /**
     * 设置继承模式
     *
     * @param inheritMode 继承模式
     */
    public void setInheritMode(String inheritMode) { this.inheritMode = inheritMode; }

    /**
     * 设置操作编码集合
     *
     * @param operationCodes 操作编码集合
     */
    public void setOperationCodes(Set<String> operationCodes) { this.operationCodes = operationCodes; }

    /**
     * 设置操作权限ID集合
     *
     * @param operationPermissionIds 操作权限ID集合
     */
    public void setOperationPermissionIds(Set<Long> operationPermissionIds) { this.operationPermissionIds = operationPermissionIds; }

    /**
     * 设置执行上下文
     *
     * @param context 执行上下文
     */
    public void setContext(Map<String, Object> context) { this.context = context; }

    // ===== 标志位Setter方法 =====

    /**
     * 设置是否查询全范围权限
     *
     * @param v 是否查询
     */
    public void setQueryScopeAll(boolean v) { this.queryScopeAll = v; }

    /**
     * 设置是否查询实例级权限
     *
     * @param v 是否查询
     */
    public void setQueryInstance(boolean v) { this.queryInstance = v; }

    /**
     * 设置全范围匹配时是否提前返回
     *
     * @param v 是否提前返回
     */
    public void setEarlyReturnOnScopeAll(boolean v) { this.earlyReturnOnScopeAll = v; }

    /**
     * 设置是否继承父资源权限
     *
     * @param v 是否继承
     */
    public void setInheritParents(boolean v) { this.inheritParents = v; }

    /**
     * 设置是否继承子资源权限
     *
     * @param v 是否继承
     */
    public void setInheritChildren(boolean v) { this.inheritChildren = v; }

    /**
     * 设置是否评估条件
     *
     * @param v 是否评估
     */
    public void setEvaluateConditions(boolean v) { this.evaluateConditions = v; }

    /**
     * 设置是否评估冲突规则
     *
     * @param v 是否评估
     */
    public void setEvaluateConflicts(boolean v) { this.evaluateConflicts = v; }

    /**
     * 设置是否评估位匹配
     *
     * @param v 是否评估
     */
    public void setEvaluateMatchesBit(boolean v) { this.evaluateMatchesBit = v; }

    /**
     * 设置是否为用户视图查询模式
     *
     * @param v 是否为用户视图模式
     */
    public void setForUserView(boolean v) { this.forUserView = v; }

    /**
     * 设置是否包含资源信息
     *
     * @param v 是否包含
     */
    public void setIncludeResources(boolean v) { this.includeResources = v; }

    /**
     * 设置是否包含操作信息
     *
     * @param v 是否包含
     */
    public void setIncludeOperations(boolean v) { this.includeOperations = v; }

    /**
     * 设置是否包含角色信息
     *
     * @param v 是否包含
     */
    public void setIncludeRoles(boolean v) { this.includeRoles = v; }

    public void setIncludeDomains(boolean v) { this.includeDomains = v; }

    public void setIncludeConditions(boolean v) { this.includeConditions = v; }

    /**
     * 设置是否使用角色缓存
     *
     * @param v 是否使用
     */
    public void setUseRoleCache(boolean v) { this.useRoleCache = v; }
}