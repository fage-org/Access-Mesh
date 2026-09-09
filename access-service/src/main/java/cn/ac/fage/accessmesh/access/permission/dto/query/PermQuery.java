package cn.ac.fage.accessmesh.access.permission.dto.query;

import cn.ac.fage.accessmesh.access.permission.enums.TargetMode;
import lombok.Setter;

import java.util.*;

/**
 * 统一权限查询输入类（T-PERM-057 统一引擎入参模型，query-engine-unification.md §3）
 * <p>
 * 一个引擎、一套入参、一个结果模型；多入口 = 参数预设的封装（六工厂 + 引擎四便捷入口）。
 * 目标模式三态互不串义：TYPE_LEVEL 只消费 scopeAll（不做实例查询）、INSTANCE 目标下推
 * （判定面继承开启时目标集扩为 {目标}∪同类型祖先链闭包）、LIST 按角色全量。
 * </p>
 * <p>
 * 两语义拆分（Q1 定案）：
 * <ul>
 *   <li>判定面继承（{@link #inheritClosure}）：作用在<b>查询前</b>扩大目标集——改变 allowed/denied；</li>
 *   <li>展示面展开（{@link #inheritParents}/{@link #inheritChildren}）：作用在<b>查询后</b>克隆结果行
 *       （grantSource=INHERITED）——不改变判定，只改变返回集合内容（清单面树扩展归口本轨道）。</li>
 * </ul>
 * 调用者使用静态工厂方法创建；自定义字段可在创建后设置。
 * </p>
 */
@Setter
public class PermQuery {

    // ── 查询主体（userId→roleIds 解析属入口封装层，引擎核心管线只认角色） ──

    /**
     * 租户ID（必填）
     */
    private final Long tenantId;

    /**
     * 用户ID（入口封装层解析为角色集合；引擎核心管线只消费 {@link #roleIds}）
     */
    private Long userId;

    /**
     * 角色ID集合（可选，直接给定角色级查询；优先于 userId 解析）
     */
    private Set<Long> roleIds;

    // ── 目标（targetMode 三态判别，Q2 定案） ──

    /**
     * 目标模式：TYPE_LEVEL（只消费 scopeAll）/ INSTANCE（实例判定，目标下推+闭包）/ LIST（按角色全量）
     */
    private TargetMode targetMode;

    /**
     * 资源类型编码集合
     */
    private Set<String> resourceTypeCodes;

    /**
     * 资源编码集合（INSTANCE 编码目标；与 {@link #codeType} 组成目标三元组）
     */
    private Set<String> resourceCodes;

    /**
     * 编码类型（CODE/ID 等；编码目标的 ResourceResolveRequest 键组成部分，不归结果组装层）
     */
    private String codeType;

    /**
     * 资源实体ID集合（INSTANCE 实体 id 目标；与编码目标二选一，同时给出时以实体 id 为准）
     */
    private Set<Long> resourceEntityIds;

    /**
     * 业务域编码（解析期参与 ResourceResolveRequest；引擎结果模型不感知域分类）
     */
    private String domainCode;

    // ── 操作 ──

    /**
     * 操作编码集合（位覆盖常开不可关——MANAGE⊇VIEW、inheritMask 继承是引擎固有语义）
     */
    private Set<String> operationCodes;

    /**
     * 操作权限ID集合（内部直给形态，优先于 operationCodes 解析）
     */
    private Set<Long> operationPermissionIds;

    // ── 判定面继承（查询前目标闭包，仅 INSTANCE 模式生效；Q12 默认值矩阵） ──

    /**
     * 判定面继承开关：查目标 X 时把 X∪同类型祖先链作为查询目标集（止步同类型、软删截断，
     * 2026-09-09 定案）——改变 allowed/denied 结果。
     * <p>默认值按入口矩阵：管理面写门禁/读过滤面开；/auth-check 关+inheritMode 参数显式开；
     * 网关快照天然关（API 扁平无树）；清单/视图面不适用（无目标集）。</p>
     */
    private boolean inheritClosure;

    // ── 展示面展开（查询后条目克隆，grantSource=INHERITED；不改变判定） ──

    /**
     * 展示面展开：是否向父方向克隆结果条目（清单面 includeInherited 契约字段收编）
     */
    private boolean inheritParents;

    /**
     * 展示面展开：是否向子方向克隆结果条目（清单面 includeChildren 契约字段收编）
     */
    private boolean inheritChildren;

    // ── 主资源上下文（数据范围面一等入参；query-scopes parentResource* 参数族收编，Q4） ──

    /**
     * 主资源类型编码（depend_on 子权限过滤的父资源上下文；LIST 模式消费）
     */
    private String parentResourceTypeCode;

    /**
     * 主资源编码
     */
    private String parentResourceCode;

    /**
     * 主资源编码类型
     */
    private String parentCodeType;

    /**
     * 主资源操作编码集合（引擎内部先对主资源做 INSTANCE 判定，dependOn 过滤消费其命中权限 id 集）
     */
    private Set<String> parentOperationCodes;

    // ── 评估 ──

    /**
     * 是否评估条件（三态之评估开关；标记态见 {@link #markConditionsOnly}）
     */
    private boolean evaluateConditions = true;

    /**
     * 是否仅标记条件不过滤（T-PERM-017 C3，三态之标记下发）
     * <p>
     * 当 {@code true} 时，引擎跳过 {@code conditionDomainService.evaluate} 的过滤逻辑，
     * 条件条目原样保留进结果，由调用方（如 SnapshotAssembler/Gateway）决定下发与重评。
     * 与 {@link #evaluateConditions} 的关系：本标志在 evaluateConditions=true 时生效，
     * 表示"评估开关开启但选择不过滤"。
     * </p>
     */
    private boolean markConditionsOnly;

    /**
     * 是否评估条目级冲突过滤（PERM_MUTEX 条目互斥，入参化按需开启，2026-09-09 定案；
     * 默认按入口：运行时面开、配置面关。角色互斥不归引擎——授权时校验另立项，
     * 快照/权限树的 filterRoleMutex 由调用方自理）
     */
    private boolean evaluateConflicts = true;

    /**
     * 是否评估操作位语义（SQL 掩码计算常开不受此开关影响；本开关控制操作辅助装配与位覆盖投影展开）
     */
    private boolean evaluateMatchesBit = true;

    // ── 条件上下文（多层：用户环境 clientIp + 服务器环境 evaluatedAt + 调用方上下文） ──

    /**
     * 条件评估上下文（2026-09-09 定案：专门多层对象）
     */
    private PermEvalContext evalContext;

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

    // ===== 工厂方法（参数预设封装；位覆盖掩码计算常开） =====

    /**
     * 创建单条权限校验查询（/auth/check 族，运行时面）
     * <p>
     * 无编码目标 = TYPE_LEVEL（只消费 scopeAll）；有编码目标 = INSTANCE。
     * 条件评估开、条目互斥开；判定面继承默认关（矩阵：SDK 契约关 + inheritMode 参数显式开）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param userId           用户ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码，null 表示类型级校验
     * @param operationCode    操作编码
     * @return 权限查询实例
     */
    public static PermQuery forAuthCheck(Long tenantId, Long userId,
                                          String resourceTypeCode, String resourceCode,
                                          String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCode == null ? Set.of() : Set.of(resourceTypeCode);
        q.targetMode = resourceCode == null ? TargetMode.TYPE_LEVEL : TargetMode.INSTANCE;
        q.resourceCodes = resourceCode == null ? null : Set.of(resourceCode);
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.evaluateMatchesBit = true;
        return q;
    }

    /**
     * 创建接口权限校验查询（check-interface，网关回退链路）
     * <p>
     * INSTANCE（entityId 目标集合）；判定面继承天然关（API 类型扁平无树）。
     * 完整评估，返回所有辅助信息。
     * </p>
     *
     * @param tenantId          租户ID
     * @param userId            用户ID
     * @param resourceTypeCodes 资源类型编码集合
     * @param resourceEntityIds 资源实体ID集合
     * @param operationCode     操作编码
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
        q.targetMode = TargetMode.INSTANCE;
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
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
     * 创建管理操作验证查询（管理面写门禁，code 轨）
     * <p>
     * 无编码目标 = TYPE_LEVEL；有编码目标 = INSTANCE。评估口径拉平（Q13 定案，2026-09-09）：
     * 条件评估开（挂条件授权评估后判定，入口封装层自动装配当前请求 clientIp）、
     * 条目互斥开（运行时面）、判定面继承开（管理面写门禁矩阵——授权在父资源、查子资源判定通过）。
     * 主体必须是权限域投影主体（{@code abstract_user.id}），禁止直接传 admin 域
     * （T-ORG-001 统一后操作者 ID 即主体 ID，无转换层）。
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
        q.targetMode = resourceCode == null ? TargetMode.TYPE_LEVEL : TargetMode.INSTANCE;
        q.resourceCodes = resourceCode == null ? null : Set.of(resourceCode);
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.inheritClosure = true;
        return q;
    }

    /**
     * 创建管理操作验证查询（管理面写门禁，resource_entity.id 轨）
     * <p>
     * 与 {@link #forValidate} 相同的目标三态与拉平口径（评估开/互斥开/判定面继承开），
     * 但实例目标直接以 {@code resource_entity.id} 给出，跳过 code 解析。
     * 仅限引擎内部与已完成解析的调用方（资源树、API 映射、资源依赖、权限树等
     * 直接管理资源实体的后台链路）使用，禁止用于 USER/ROLE 等业务对象门禁。
     * 主体必须是权限域投影主体（{@code abstract_user.id}），禁止直接传 admin 域
     * （T-ORG-001 统一后操作者 ID 即主体 ID，无转换层）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型编码
     * @param resourceEntityId resource_entity.id，null 表示仅类型级校验
     * @param operationCode    操作编码
     * @return 权限查询实例
     */
    public static PermQuery forValidateByEntityId(Long tenantId, Long subjectId,
                                                   String resourceTypeCode, Long resourceEntityId,
                                                   String operationCode) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = subjectId;
        q.resourceTypeCodes = resourceTypeCode == null ? Set.of() : Set.of(resourceTypeCode);
        q.resourceEntityIds = resourceEntityId == null ? null : Set.of(resourceEntityId);
        q.targetMode = resourceEntityId == null ? TargetMode.TYPE_LEVEL : TargetMode.INSTANCE;
        q.operationCodes = operationCode == null ? Set.of() : Set.of(operationCode);
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.inheritClosure = true;
        return q;
    }

    /**
     * 创建范围查询（数据范围面，query-scopes 消费）
     * <p>
     * LIST（按角色全量拉取角色权限行——修复旧形态 queryInstance=true 却无目标导致实例条目
     * 永不返回的缺陷，INSTANCE 四态自此可达）；条件评估开、条目互斥开（运行时面）；
     * depend_on 子权限过滤经主资源上下文（parentResource* setter）由引擎执行。
     * </p>
     *
     * @param tenantId          租户ID
     * @param userId            用户ID
     * @param resourceTypeCodes 资源类型编码集合
     * @param operationCodes    操作编码集合
     * @return 权限查询实例
     */
    public static PermQuery forScopeQuery(Long tenantId, Long userId,
                                           Set<String> resourceTypeCodes,
                                           Set<String> operationCodes) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.resourceTypeCodes = resourceTypeCodes;
        q.targetMode = TargetMode.LIST;
        q.operationCodes = operationCodes;
        q.evaluateConditions = true;
        q.evaluateConflicts = true;
        q.includeResources = true;
        q.includeOperations = true;
        q.includeRoles = true;
        return q;
    }

    /**
     * 创建用户视图查询（清单/视图面：query-resources、权限串、快照构建）
     * <p>
     * LIST（按角色全量，ROLE_PERM_SNAPSHOT 读缓存）；条件评估开（标记态经
     * {@code setMarkConditionsOnly(true)} 切换，快照构建消费）、条目互斥开。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 权限查询实例
     */
    public static PermQuery forUserView(Long tenantId, Long userId) {
        PermQuery q = new PermQuery(tenantId);
        q.userId = userId;
        q.targetMode = TargetMode.LIST;
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
    public TargetMode targetMode() { return targetMode; }
    public Set<String> resourceTypeCodes() { return resourceTypeCodes; }
    public Set<String> resourceCodes() { return resourceCodes; }
    public String codeType() { return codeType; }
    public Set<Long> resourceEntityIds() { return resourceEntityIds; }
    public String domainCode() { return domainCode; }
    public Set<String> operationCodes() { return operationCodes; }
    public Set<Long> operationPermissionIds() { return operationPermissionIds; }
    public boolean inheritClosure() { return inheritClosure; }
    public boolean inheritParents() { return inheritParents; }
    public boolean inheritChildren() { return inheritChildren; }
    public String parentResourceTypeCode() { return parentResourceTypeCode; }
    public String parentResourceCode() { return parentResourceCode; }
    public String parentCodeType() { return parentCodeType; }
    public Set<String> parentOperationCodes() { return parentOperationCodes; }
    public boolean evaluateConditions() { return evaluateConditions; }
    public boolean markConditionsOnly() { return markConditionsOnly; }
    public boolean evaluateConflicts() { return evaluateConflicts; }
    public boolean evaluateMatchesBit() { return evaluateMatchesBit; }
    public PermEvalContext evalContext() { return evalContext; }
    public boolean includeResources() { return includeResources; }
    public boolean includeOperations() { return includeOperations; }
    public boolean includeRoles() { return includeRoles; }
    public boolean includeDomains() { return includeDomains; }
    public boolean includeConditions() { return includeConditions; }
    public boolean useRoleCache() { return useRoleCache; }

    /**
     * 设置主资源上下文（数据范围面 depend_on 子权限过滤，query-scopes 收编）。
     *
     * @param resourceTypeCode 主资源类型编码
     * @param resourceCode     主资源编码
     * @param codeType         主资源编码类型
     * @param operationCodes   主资源操作编码集合
     */
    public void setParentResource(String resourceTypeCode, String resourceCode,
                                  String codeType, Set<String> operationCodes) {
        this.parentResourceTypeCode = resourceTypeCode;
        this.parentResourceCode = resourceCode;
        this.parentCodeType = codeType;
        this.parentOperationCodes = operationCodes;
    }

    /**
     * 设置继承模式（/auth/check 契约参数；接通为判定面闭包真实语义——Q12 定案：
     * 该参数从「对单点判定结论无效」接通为目标闭包）。
     * <ul>
     *   <li>"PARENT"/"BOTH" → inheritClosure=true（判定面继承开，allowed 随祖先授权变化）</li>
     *   <li>"CHILD"/"NONE"/其他 → 判定面不适用（子授权不覆盖父判定）</li>
     * </ul>
     *
     * @param inheritMode 继承模式
     */
    public void setInheritMode(String inheritMode) {
        if ("PARENT".equalsIgnoreCase(inheritMode) || "BOTH".equalsIgnoreCase(inheritMode)) {
            this.inheritClosure = true;
        } else {
            this.inheritClosure = false;
        }
    }
}
