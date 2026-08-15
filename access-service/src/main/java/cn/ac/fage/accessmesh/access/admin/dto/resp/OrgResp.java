package cn.ac.fage.accessmesh.access.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织响应记录类
 * <p>
 * 用于返回组织信息，支持树形结构。
 * 包含组织类型、名称、父级组织、编码、状态、排序、子组织列表。
 * 注：phone/email 字段已移除——sys_org 实体/表不含联系方式字段
 * （：删除契约+响应字段，与 DTO 对齐）。
 * </p>
 *
 * @param id          组织ID
 * @param orgType     组织类型
 * @param orgName     组织名称
 * @param parentOrgId 父级组织ID
 * @param code        组织编码
 * @param status      状态（0=正常，1=禁用）
 * @param sort        排序号
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 * @param children    子组织列表（树形结构）
 */
public record OrgResp(
    /**
     * 组织ID
     */
    Long id,

    /**
     * 组织类型
     */
    Integer orgType,

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
     * 状态（0=正常，1=禁用）
     */
    Integer status,

    /**
     * 排序号
     */
    Integer sort,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt,

    /**
     * 子组织列表（树形结构）
     */
    List<OrgResp> children
) {}