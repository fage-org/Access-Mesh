package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 组织创建请求记录类
 * <p>
 * 用于创建新组织的请求参数。
 * 包含组织类型、名称、父级组织、编码、联系方式等。
 * </p>
 *
 * @param orgType    组织类型（必填）
 * @param orgName    组织名称（必填）
 * @param parentOrgId 父级组织ID（可选，null表示顶级组织）
 * @param code       组织编码（可选）
 * @param phone      联系电话（可选）
 * @param email      联系邮箱（可选）
 * @param status     状态（可选，默认0=正常）
 * @param sort       排序号（可选）
 */
public record OrgCreateReq(
    /**
     * 组织类型
     */
    @NotNull(message = "组织类型不能为空")
    Integer orgType,

    /**
     * 组织名称
     */
    @NotBlank(message = "组织名称不能为空")
    String orgName,

    /**
     * 父级组织ID（null表示顶级组织）
     */
    String parentOrgId,

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