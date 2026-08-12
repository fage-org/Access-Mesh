package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 组织更新请求记录类
 * <p>
 * 用于更新组织信息的请求参数。
 * 所有字段均为可选，仅更新提供的字段。
 * </p>
 *
 * @param id          组织ID（必填，用于定位组织）
 * @param orgName     组织名称（可选）
 * @param parentOrgId 父级组织ID（可选）
 * @param code        组织编码（可选）
 * @param phone       联系电话（可选）
 * @param email       联系邮箱（可选）
 * @param status      状态（可选）
 * @param sort        排序号（可选）
 */
public record OrgUpdateReq(
    /**
     * 组织ID
     */
    @NotNull(message = "组织ID不能为空")
    Long id,

    /**
     * 组织名称
     */
    String orgName,

    /**
     * 父级组织ID
     */
    Long parentOrgId,

    /**
     * 组织编码
     */
    String code,

    /**
     * 联系电话
     */
    String phone,

    /**
     * 联系邮箱
     */
    String email,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status,

    /**
     * 排序号
     */
    Integer sort
) {}