package cn.ac.fage.accessmesh.access.permission.dto.query;

import lombok.Setter;

import java.util.*;

/**
 * 统一权限查询输入类
 * <p>
 * 所有查询场景使用此单一DTO。
 * 调用者使用静态工厂方法创建；自定义字段可在创建后设置。
 * </p>
 */
@Setter
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

    /**
     * 业务域编码
     */
    private String domainCode;

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
     * 是否仅标记条件不过滤（T-PERM-017 C3）
     * <p>
     * 当 {@code true} 时，引擎跳过 {@code conditionDomainService.evaluate} 的过滤逻辑，
     * 条件条目原样保留进结果，由调用方（如 SnapshotAssembler）决定下发与否。
     * 与 {@link #evaluateConditions} 的关系：本标志在 evaluateConditions=true 时生效，
     * 表示"评估开关开启但选择不过滤"。
     * </p>
     * <p>
     * 使用场景：interfaceSnapshot 构建快照时，条件评估应在 Gateway 用真实请求 context
     * 完成（而非 permission-center 用空 context），故 permission-center 此处只标记不过滤。
     * </p>
     */
    private boolean markConditionsOnly;

    /**
     * 是否评估冲突规则
     */
    private boolean evaluateConflicts = true;

    /**
     * 是否评估操作位语义。
     * <p>
     * 目标操作查询中，位语义用于按 effectiveBits 计算 SQL 查询掩码；
     * 用户视图查询中，位语义用于把显式授权操作展开为最终可用操作投影。
     * </p>
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
     * 创建资源检查查询
     * <p>
     * 全范围+实例查询，完整评估（条件/冲突/位匹配），包含资源和操作信息。
     * 与 {@link #forResourceQuery} 相比，此方法开启条件评估和冲突评估。
     * </p>
     *
     * @param tenantId          租户ID
     * @param userId            用户ID
     * @param resourceTypeCodes 资源类型编码集合
     * @param operationCodes    操作编码集合
     * @return 权限查询实例
     */
    public static PermQuery forResourceCheck(Long tenantId, Long userId,
                                              Set<String> resourceTypeCodes,
                                              Set<String> operationCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.operationCodes = operationCodes;
        q.queryScopeAll = true;
        q.queryInstance = true;
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.evaluateMatchesBit = true;
        q.includeResources = true;
        q.includeOperations = true;
        return q;
    }

    /**
     * 创建管理操作验证查询
     * <p>
     * 类型+实例查询，全范围匹配时提前返回，不评估，最小输出。
     * 主体必须是权限域投影主体（{@code abstract_user.id}），禁止直接传 admin 域
     * {@code sys_user.id}（先经 {@code OperatorSubjectResolver.requireSubjectId} 转换）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param operationCode    操作编码
     * @return 权限查询实例
     */
    public static PermQuery forValidate(Long tenantId, Long subjectId,
                                         String resourceTypeCode, String resourceCode,
                                         String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = subjectId;
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
     * 查询全部角色权限记录，不按位过滤；随后按 effectiveBits 展开最终可用操作投影。
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
        q.evaluateMatchesBit = true;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        return q;
    }

    // ===== Getter方法（保留原有 xxx() 形式） =====

    public Long tenantId() { return tenantId; }
    public Long userId() { return userId; }
    public Set<Long> roleIds() { return roleIds; }
    public Set<String> resourceTypeCodes() { return resourceTypeCodes; }
    public Set<String> resourceCodes() { return resourceCodes; }
    public String codeType() { return codeType; }
    public Set<Long> resourceEntityIds() { return resourceEntityIds; }
    public String inheritMode() { return inheritMode; }
    public String domainCode() { return domainCode; }
    public Set<String> operationCodes() { return operationCodes; }
    public Set<Long> operationPermissionIds() { return operationPermissionIds; }
    public boolean queryScopeAll() { return queryScopeAll; }
    public boolean queryInstance() { return queryInstance; }
    public boolean earlyReturnOnScopeAll() { return earlyReturnOnScopeAll; }
    public boolean inheritParents() { return inheritParents; }
    public boolean inheritChildren() { return inheritChildren; }
    public boolean evaluateConditions() { return evaluateConditions; }
    public boolean markConditionsOnly() { return markConditionsOnly; }
    public boolean evaluateConflicts() { return evaluateConflicts; }
    public boolean evaluateMatchesBit() { return evaluateMatchesBit; }
    public boolean forUserView() { return forUserView; }
    public boolean includeResources() { return includeResources; }
    public boolean includeOperations() { return includeOperations; }
    public boolean includeRoles() { return includeRoles; }
    public boolean includeDomains() { return includeDomains; }
    public boolean includeConditions() { return includeConditions; }
    public boolean useRoleCache() { return useRoleCache; }
    public Map<String, Object> context() { return context; }

    /**
     * 设置继承模式，同时将字符串转换为布尔位
     * <ul>
     *   <li>"PARENT" → inheritParents=true</li>
     *   <li>"CHILD" → inheritChildren=true</li>
     *   <li>"BOTH" → 两者都true</li>
     *   <li>"NONE" 或其他 → 两者都false</li>
     * </ul>
     *
     * @param inheritMode 继承模式
     */
    public void setInheritMode(String inheritMode) {
        this.inheritMode = inheritMode;
        if ("PARENT".equalsIgnoreCase(inheritMode)) {
            this.inheritParents = true;
            this.inheritChildren = false;
        } else if ("CHILD".equalsIgnoreCase(inheritMode)) {
            this.inheritParents = false;
            this.inheritChildren = true;
        } else if ("BOTH".equalsIgnoreCase(inheritMode)) {
            this.inheritParents = true;
            this.inheritChildren = true;
        } else {
            this.inheritParents = false;
            this.inheritChildren = false;
        }
    }
}
