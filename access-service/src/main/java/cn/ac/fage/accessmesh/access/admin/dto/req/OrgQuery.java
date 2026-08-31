package cn.ac.fage.accessmesh.access.admin.dto.req;

/**
 * 组织查询请求记录类
 * <p>
 * 用于组织列表的条件查询参数。
 * 支持按名称、类型、状态、父级组织过滤。
 * </p>
 *
 * @param orgName    组织名称（可选，模糊匹配）
 * @param orgType    组织类型（可选）
 * @param status     状态（可选，0=正常，1=禁用）
 * @param parentOrgId 父级组织ID（可选，用于查询子组织）
 */
public record OrgQuery(
    /**
     * 组织名称（模糊匹配）
     */
    String orgName,

    /**
     * 组织类型
     */
    Integer orgType,

    /**
     * 状态（1=启用，0=停用——全系统统一口径，DDL DEFAULT 1 同源；T-FE-015 联调订正笔误）
     */
    Integer status,

    /**
     * 父级组织ID（用于查询子组织）
     */
    Long parentOrgId
) {}