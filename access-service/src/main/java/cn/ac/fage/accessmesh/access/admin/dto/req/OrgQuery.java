package cn.ac.fage.accessmesh.access.admin.dto.req;

/**
 * 组织查询请求记录类
 * <p>
 * 用于组织列表的条件查询参数。
 * 支持按名称、类型、状态、父级组织、组织树配置过滤。
 * </p>
 *
 * @param operationCode   操作语义（可选：VIEW=可视范围 / CREATE=新增用户挂载点，缺省 VIEW；T-ADMIN-021 债务①落地）
 * @param treeConfigId    组织树配置 ID（可选；不传=默认树子树；operationCode=CREATE 时禁止传，T-ADMIN-021 用户决策）
 * @param orgName         组织名称（可选，模糊匹配——保留自身或后代命中的分支，T-ADMIN-021 修正原死参数）
 * @param orgType         组织类型（1/2 白名单；includePositions != true 时必填——tree 查询按 orgType 分发 VIEW/VIEW_POSITION 门禁，缺省报 ORG_TYPE_REQUIRED；includePositions=true 时忽略）
 * @param includePositions 是否返回组织+岗位一体树（可选，默认 false 行为与单类型语义一致；T-ADMIN-021）
 * @param status          状态（可选，1=启用，0=停用——全系统统一口径，DDL DEFAULT 1 同源）
 * @param parentOrgId     父级组织ID（可选，用于在配置子树内再取该节点子树；一般不与 treeConfigId 同时使用）
 */
public record OrgQuery(
    /**
     * 操作语义：VIEW=可视范围，CREATE=新增用户挂载点（限默认树）
     */
    String operationCode,

    /**
     * 组织树配置 ID；不传则按默认树（is_default=true）配置裁剪子树
     */
    Long treeConfigId,

    /**
     * 组织名称（模糊匹配）
     */
    String orgName,

    /**
     * 组织类型（includePositions != true 时必填；includePositions=true 时忽略）
     */
    Integer orgType,

    /**
     * 是否返回组织+岗位一体树（默认 false）
     */
    Boolean includePositions,

    /**
     * 状态（1=启用，0=停用——全系统统一口径，DDL DEFAULT 1 同源；T-FE-015 联调订正笔误）
     */
    Integer status,

    /**
     * 父级组织ID（可选，在配置子树内再取该节点为顶层的子树；不在子树范围内=空结果）
     */
    Long parentOrgId
) {}
