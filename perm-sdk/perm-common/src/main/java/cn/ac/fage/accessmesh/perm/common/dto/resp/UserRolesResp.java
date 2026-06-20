package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户角色列表响应
 * <p>
 * 用于返回用户在权限中心的关联角色列表。
 * 仅使用稳定的业务键标识。
 * </p>
 */
public record UserRolesResp(
    /**
     * 主体类型码
     */
    String subjectTypeCode,
    /**
     * 主体外部ID
     */
    String subjectExternalId,
    /**
     * 角色摘要列表
     */
    List<RoleSummary> roles
) {
    /**
     * 角色摘要信息
     * <p>
     * 表示单个角色的基本信息。
     * </p>
     */
    public record RoleSummary(
        /**
         * 角色外部ID
         */
        String roleExternalId,
        /**
         * 角色名称
         */
        String roleName,
        /**
         * 角色类型码
         */
        String roleTypeCode,
        /**
         * 目标类型
         */
        String targetType,
        /**
         * 关联关系ID（关联组织角色 abstract_role.id，permission-center 内部主键）
         */
        Long relationId,
        /**
         * 关联组织角色外部ID（= sys_org.id 字符串，供 admin 解析组织名；仅 POSITION 等带关联组织角色的关系非空）
         */
        String relationExternalId,
        /**
         * 有效起始时间
         */
        LocalDateTime validFrom,
        /**
         * 有效截止时间
         */
        LocalDateTime validTo
    ) {}
}
