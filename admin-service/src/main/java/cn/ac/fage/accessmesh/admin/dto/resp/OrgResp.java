package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织响应记录类
 * <p>
 * 用于返回组织信息，支持树形结构。
 * 包含组织类型、名称、父级组织、编码、联系方式、状态、排序、子组织列表。
 * </p>
 *
 * @param id          组织ID
 * @param orgType     组织类型
 * @param orgName     组织名称
 * @param parentOrgId 父级组织ID
 * @param code        组织编码
 * @param phone       联系电话
 * @param email       联系邮箱
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