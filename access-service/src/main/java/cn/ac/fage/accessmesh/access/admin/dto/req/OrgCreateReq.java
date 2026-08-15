package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 组织创建请求记录类
 * <p>
 * 用于创建新组织的请求参数。
 * 包含组织类型、名称、父级组织、编码等。
 * 注：原 DTO 中的 phone/email 字段已移除——sys_org 实体不含联系方式字段
 * （声明的字段必须生效，不生效的字段不得声明）。
 * </p>
 *
 * @param orgType     组织类型（必填）
 * @param orgName     组织名称（必填）
 * @param parentOrgId 父级组织ID（可选，null表示顶级组织）
 * @param code        组织编码（可选）
 * @param status      状态（可选，0=禁用，1=正常）
 * @param sort        排序号（可选）
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
    Long parentOrgId,

    /**
     * 组织编码
     */
    String code,

    /**
     * 状态（0=禁用，1=正常）
     */
    Integer status,

    /**
     * 排序号
     */
    Integer sort
) {}