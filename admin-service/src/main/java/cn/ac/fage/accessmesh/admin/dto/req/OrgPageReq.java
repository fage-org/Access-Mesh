package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 组织分页查询请求记录类
 * <p>
 * 用于组织列表的分页查询参数。
 * 合并分页参数和查询条件，支持按名称、类型、状态、子树筛选。
 * </p>
 *
 * @param pageNum     页码（可选，默认1，最小1）
 * @param pageSize    每页大小（可选，默认20，范围1-100）
 * @param sort        排序字段（可选）
 * @param orgName     组织名称（可选，模糊匹配）
 * @param orgType     组织类型（可选）
 * @param status      状态（可选，0=正常，1=禁用）
 * @param parentOrgId 父级组织ID（可选，用于查询子组织）
 * @param orgId       子树根组织ID（可选，返回该组织及其子孙，岗位Tab筛选用）
 */
public record OrgPageReq(
    /**
     * 页码（最小1）
     */
    @Min(1) Integer pageNum,

    /**
     * 每页大小（范围1-100）
     */
    @Min(1) @Max(100) Integer pageSize,

    /**
     * 排序字段
     */
    String sort,

    /**
     * 组织名称（模糊匹配）
     */
    String orgName,

    /**
     * 组织类型
     */
    Integer orgType,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status,

    /**
     * 父级组织ID（用于查询子组织）
     */
    Long parentOrgId,

    /**
     * 子树根组织ID（可选）
     * <p>
     * 传入时仅返回该组织及其所有子孙组织。
     * 用于岗位 Tab 按选中组织筛选：前端传 { orgType: 2, orgId: 选中组织ID }。
     * </p>
     */
    Long orgId
) {
    /**
     * 获取页码（默认1）
     *
     * @return 页码
     */
    public int getPageNum() { return pageNum != null ? pageNum : 1; }

    /**
     * 获取每页大小（默认20）
     *
     * @return 每页大小
     */
    public int getPageSize() { return pageSize != null ? pageSize : 20; }
}