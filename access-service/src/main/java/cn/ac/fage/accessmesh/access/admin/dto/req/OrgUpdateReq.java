package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 组织更新请求记录类
 * <p>
 * 用于更新组织信息的请求参数。
 * 所有字段均为可选，仅更新提供的字段（null 跳过，保留原值）。
 * 注：原 DTO 中的 phone/email 字段已移除——sys_org 实体不含联系方式字段
 * （T-ACCESS-005 评审 P1：声明的字段必须生效，不生效的字段不得声明）。
 * </p>
 *
 * @param id          组织ID（必填，用于定位组织）
 * @param orgName     组织名称（可选）
 * @param parentOrgId 父级组织ID（可选，null 表示不移动）
 * @param code        组织编码（可选）
 * @param status      状态（可选，0=禁用，1=正常）
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
     * 状态（0=禁用，1=正常）
     */
    Integer status,

    /**
     * 排序号
     */
    Integer sort
) {}