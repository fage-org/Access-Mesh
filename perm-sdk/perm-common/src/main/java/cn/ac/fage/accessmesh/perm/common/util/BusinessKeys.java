package cn.ac.fage.accessmesh.perm.common.util;

/**
 * 后端业务键统一构造/解析入口（T-PERM-019 D2）。
 * <p>
 * 收敛 access-service 后端散落的业务键拼接点，消灭「读代码才知道格式」的隐式约定：
 * 同一格式的构造与消费分散在多个类时（如类型解析缓存键由 TypeResolutionServiceImpl 写入、
 * TypeDefinitionAppServiceImpl 失效），任何一侧手改格式都会造成静默错配。本类是这些键族的
 * 唯一构造点，格式由 {@code BusinessKeysParityTest} 以 golden 值锁定，改格式即测试失败。
 * </p>
 *
 * <ul>
 *   <li>范围：后端全部业务键（跨类格式契约键 + 单文件内部映射键）；前端 TS 辅助不动。</li>
 *   <li>出界：sync API 契约键（percent-encoded businessKey/scopeKey）归 access-service 的
 *       {@code SyncKeyCodec}，与本类互不替代；缓存框架存储信封、Gateway 本地快照键、
 *       登录计数/任务幂等/树写锁等基础设施键、错误文案拼接均不经本类。</li>
 *   <li>大小写口径（2026-09-07 定案：保持各点现状语义，后续另行统一）：本类不做大小写归一，
 *       原样拼接；授权域调用点历史上先 {@code toUpperCase()} 再拼键的，继续在调用点显式转换；
 *       唯一例外 {@link #resourceTripleCodeKey} 的大写归一是原实现自带语义，随方法整体迁入。</li>
 *   <li>方法一律用字符串拼接表达 null（null 引用拼出 {@code "null"}、显式分支拼出 {@code ""}），
 *       与被收敛的原始实现逐字节一致。</li>
 * </ul>
 */
public final class BusinessKeys {

    private BusinessKeys() {
    }

    // ---------------------------------------------------------------------
    // 类型族
    // ---------------------------------------------------------------------

    /**
     * TYPE_VALUE 解析缓存键：{@code typeKey:typeCode}。
     * <p>构造方 TypeResolutionServiceImpl（写入）、失效方 TypeDefinitionAppServiceImpl（写路径 evict），
     * 两处必须同格式（原靠 PermCacheCatalog 注释口头约定）。</p>
     */
    public static String typeValueCacheKey(String typeKey, String typeCode) {
        return typeKey + ":" + typeCode;
    }

    /**
     * TYPE_CODE 解析缓存键：{@code typeKey:typeValue}。
     * <p>同 {@link #typeValueCacheKey} 的写读分离契约，方向相反（value → code）。</p>
     */
    public static String typeCodeCacheKey(String typeKey, Integer typeValue) {
        return typeKey + ":" + typeValue;
    }

    /**
     * typeCode 服务端生成码：{@code <TYPEKEY大写>_<typeValue>}（如 {@code resource_type + 12 → RESOURCE_TYPE_12}）。
     * <p>TypeDefinitionAppServiceImpl 创建类型时 typeCode 留空的缺省生成分支。</p>
     */
    public static String generatedTypeCode(String typeKey, int typeValue) {
        return typeKey.toUpperCase() + "_" + typeValue;
    }

    /**
     * TYPE_DEFINITION 实例业务键：{@code typeKey:typeCode}。
     * <p>不可变复合键（T-PERM-051 定案，2026-09-07 预先落位）：typeCode 仅 tenant+type_key 内唯一，
     * 跨族同名（user_type 与 resource_type 均有 USER/SERVICE）必须靠复合键区分。T-PERM-051 实现
     * resource_entity 投影时必须经本方法构造，不得裸拼。</p>
     */
    public static String typeInstanceBusinessKey(String typeKey, String typeCode) {
        return typeKey + ":" + typeCode;
    }

    // ---------------------------------------------------------------------
    // 操作族
    // ---------------------------------------------------------------------

    /**
     * 操作编码键（类型码轨）：{@code resourceTypeCode:operationCode}。
     * <p>ResolveContext 解析缓存、AccessBootstrapInitializer 固定图 bits 映射等；不做大小写归一。</p>
     */
    public static String operationCodeKey(String resourceTypeCode, String operationCode) {
        return resourceTypeCode + ":" + operationCode;
    }

    /**
     * 操作编码键（类型值轨）：{@code resourceTypeValue:operationCode}。
     * <p>操作定义/权限查询/授权域的内部映射；不做大小写归一，授权域调用点需要容错时先显式
     * {@code toUpperCase()} 再进本方法。</p>
     */
    public static String operationCodeKey(Integer resourceTypeValue, String operationCode) {
        return resourceTypeValue + ":" + operationCode;
    }

    /**
     * 操作位键：{@code resourceTypeValue:binaryBit}（resourceTypeValue 为 null 时拼 {@code "NULL"}）。
     * <p>OperationPermissionUtils 位掩码配对、授权域 grantedBits 分组。</p>
     */
    public static String operationBitKey(Integer resourceTypeValue, Long binaryBit) {
        return (resourceTypeValue == null ? "NULL" : String.valueOf(resourceTypeValue)) + ":" + binaryBit;
    }

    /**
     * 对外权限串：{@code resourceTypeCode:operationCode}（如 {@code USER:VIEW}）。
     * <p>effective-permission-codes 响应载荷，前端权限串匹配消费——跨前后端契约格式，改格式即破坏
     * 存量前端判定。与 {@link #operationCodeKey(String, String)} 格式相同但语义独立，分开锁定。</p>
     */
    public static String permissionCode(String resourceTypeCode, String operationCode) {
        return resourceTypeCode + ":" + operationCode;
    }

    /**
     * 授权记录键：{@code resourceTypeValue:operationCode:resourceEntityId}。
     * <p>授权域按（类型 × 操作 × 实例）聚合 RoleResourcePermission 的内存映射。</p>
     */
    public static String grantEntryKey(Integer resourceTypeValue, String operationCode, Long resourceEntityId) {
        return resourceTypeValue + ":" + operationCode + ":" + resourceEntityId;
    }

    // ---------------------------------------------------------------------
    // 资源族
    // ---------------------------------------------------------------------

    /**
     * 资源两段查找键：{@code resourceCode:codeType}。
     * <p>TypeResolutionServiceImpl 批量解析 resource_entity 时的 code+codeType 查找映射。</p>
     */
    public static String resourceCodeTypeKey(String resourceCode, String codeType) {
        return resourceCode + ":" + codeType;
    }

    /**
     * 资源三段键（值域首段）：{@code resourceTypeValue:resourceCode:codeType}。
     * <p>资源管理批量入口的请求/存量行去重映射。</p>
     */
    public static String resourceTripleValueKey(Integer resourceTypeValue, String resourceCode, String codeType) {
        return resourceTypeValue + ":" + resourceCode + ":" + codeType;
    }

    /**
     * 资源三段键（码域首段，保留原实现大写归一）：{@code <resourceTypeCode大写>:resourceCode:codeType}，
     * 三段 null 一律拼空串。
     * <p>授权域 checkCanGrant 的资源匹配键（原 PermissionGrantDomainServiceImpl#buildResourceKey 整体迁入，
     * toUpperCase 是原方法自带语义，非新增归一）。</p>
     */
    public static String resourceTripleCodeKey(String resourceTypeCode, String resourceCode, String codeType) {
        return (resourceTypeCode == null ? "" : resourceTypeCode.toUpperCase())
            + ":" + (resourceCode == null ? "" : resourceCode)
            + ":" + (codeType == null ? "" : codeType);
    }

    /**
     * 转授检查五段键：{@code resourceTypeCode:resourceCode|*:codeType|*:operationCode:ALL|SPECIFIC}。
     * <p>resourceCode/codeType 为 null 时拼 {@code "*"}。PermissionGrantDomainServiceImpl（构造结果映射）
     * 与 PermissionGrantPlanDomainServiceImpl（查表）两处共享同一 Map，格式漂移即静默
     * GRANT_CANNOT_DELEGATE——原实现两处逐字重复，收敛后唯一。</p>
     */
    public static String grantCheckKey(String resourceTypeCode, String resourceCode, String codeType,
                                       String operationCode, boolean scopeAll) {
        return resourceTypeCode
            + ":" + (resourceCode == null ? "*" : resourceCode)
            + ":" + (codeType == null ? "*" : codeType)
            + ":" + operationCode
            + ":" + (scopeAll ? "ALL" : "SPECIFIC");
    }

    // ---------------------------------------------------------------------
    // 关系族（relationKey：契约格式 TYPE:externalId，见 api-contract §6.2.2.4）
    // ---------------------------------------------------------------------

    /** relationKey 解析结果。 */
    public record RelationKeyRef(String typeCode, String externalId) {
    }

    /**
     * relationKey 构造：{@code typeCode:externalId}（如 {@code ORG:2001}）。
     * <p>契约侧格式锁定；后端内部需要构造该格式时（测试夹具、投影数据）必须经本方法。</p>
     */
    public static String relationKey(String typeCode, String externalId) {
        return typeCode + ":" + externalId;
    }

    /**
     * relationKey 解析：按第一个冒号切分为（typeCode, externalId）。
     * <p>null/空白、无冒号、类型段空、id 段空均返回 {@code null}（原 UserRoleSyncAppServiceImpl
     * 三处私有解析的统一语义：格式非法按「无关系」处理，由调用方决定报错或忽略）。</p>
     */
    public static RelationKeyRef parseRelationKey(String relationKey) {
        if (relationKey == null || relationKey.isBlank()) {
            return null;
        }
        int idx = relationKey.indexOf(':');
        if (idx <= 0 || idx == relationKey.length() - 1) {
            return null;
        }
        return new RelationKeyRef(relationKey.substring(0, idx), relationKey.substring(idx + 1));
    }

    // ---------------------------------------------------------------------
    // 单文件内部映射键（无跨类契约，统一口径防模仿裸拼）
    // ---------------------------------------------------------------------

    /**
     * 主体定位键：{@code subjectTypeCode:subjectExternalId}。
     * <p>UserManageAppServiceImpl 批量解析 subject → abstractUserId 的映射键。</p>
     */
    public static String subjectKey(String subjectTypeCode, String subjectExternalId) {
        return subjectTypeCode + ":" + subjectExternalId;
    }

    /**
     * 角色定位键：{@code roleTypeCode:domainCode|"":roleExternalId}（domainCode 为 null 拼空串）。
     * <p>UserManageAppServiceImpl 批量解析 role → roleId 的映射键。</p>
     */
    public static String roleKey(String roleTypeCode, String domainCode, String roleExternalId) {
        return roleTypeCode + ":" + (domainCode == null ? "" : domainCode) + ":" + roleExternalId;
    }

    /**
     * 用户-角色关系去重键：{@code abstractUserId:targetRoleId}。
     * <p>UserManageAppServiceImpl assign/revoke 已有关系判重。</p>
     */
    public static String userRoleRelationKey(Long abstractUserId, Long targetRoleId) {
        return abstractUserId + ":" + targetRoleId;
    }

    /**
     * 用户-角色关系匹配键（含 relationId）：{@code abstractUserId:targetRoleId:relationId|"null"}。
     * <p>UserManageAppServiceImpl 批量撤销按 relationId 精确匹配（relationId 为 null 拼字面
     * {@code "null"}，与既有匹配对侧一致）。</p>
     */
    public static String userRoleRelationIdKey(Long abstractUserId, Long targetRoleId, Long relationId) {
        return abstractUserId + ":" + targetRoleId + ":" + (relationId == null ? "null" : relationId.toString());
    }

    /**
     * 角色类型×域分组键：{@code roleTypeCode:domainCode|""}（domainCode 为 null 拼空串）。
     * <p>UserManageAppServiceImpl 批量按类型+域分组解析。</p>
     */
    public static String roleTypeDomainKey(String roleTypeCode, String domainCode) {
        return roleTypeCode + ":" + (domainCode == null ? "" : domainCode);
    }

    /**
     * 资源依赖 diff 去重键：{@code sourceId:targetId:sourceBits|0}（sourceBits 为 null 拼 0）。
     * <p>DependencyAppServiceImpl diff 快照聚合去重。</p>
     */
    public static String dependencyDiffKey(Long sourceEntityId, Long dependsOnEntityId, Long sourceBits) {
        return sourceEntityId + ":" + dependsOnEntityId + ":" + (sourceBits == null ? 0L : sourceBits);
    }

    /**
     * API 路由定位键：{@code method:path}。
     * <p>BootstrapGraphDefinition 管理清单路由去重/查找。</p>
     */
    public static String apiRouteKey(String method, String path) {
        return method + ":" + path;
    }
}
