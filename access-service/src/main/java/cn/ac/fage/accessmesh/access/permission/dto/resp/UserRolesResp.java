package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 用户角色列表响应体
 * <p>
 * 返回用户拥有的角色列表信息，包括用户标识和角色详情。
 * 用于查询用户角色关系的响应。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码
 * @param subjectExternalId 用户外部标识
 * @param roles             角色列表
 */
public record UserRolesResp(
    String subjectTypeCode,
    String subjectExternalId,
    List<RoleSummary> roles
) {
    /**
     * 角色摘要信息
     * <p>
     * 表示用户拥有的单个角色信息，包括角色标识和有效期。
     * </p>
     *
     * @param roleExternalId     角色外部标识
     * @param roleName           角色名称
     * @param roleTypeCode       角色类型编码
     * @param targetType         关系目标类型
     * @param relationId         关系ID（关联组织角色 abstract_role.id，permission-center 内部主键）
     * @param relationExternalId 关联组织角色外部标识（= sys_org.id 字符串，供 admin 解析组织名；仅 POSITION 等带关联组织角色的关系非空）
     * @param validFrom          有效期开始时间
     * @param validTo            有效期结束时间
     */
    public record RoleSummary(
        String roleExternalId,
        String roleName,
        String roleTypeCode,
        String targetType,
        Long relationId,
        String relationExternalId,
        java.time.LocalDateTime validFrom,
        java.time.LocalDateTime validTo
    ) {}
}