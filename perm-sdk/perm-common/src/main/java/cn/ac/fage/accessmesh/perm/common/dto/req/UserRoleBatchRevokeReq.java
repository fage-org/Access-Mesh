package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 用户角色批量撤销请求
 * <p>
 * 用于批量撤销用户的角色关联。
 * </p>
 */
public record UserRoleBatchRevokeReq(
    /**
     * 撤销项列表
     */
    @NotEmpty @Valid List<RevokeItem> items
) {
    /**
     * 单个撤销项
     * <p>
     * 定义单个用户角色关联的撤销请求。
     * </p>
     */
    public record RevokeItem(
        /**
         * 主体类型码
         */
        @NotBlank String subjectTypeCode,
        /**
         * 主体外部ID
         */
        @NotBlank String subjectExternalId,
        /**
         * 业务域码：功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）允许 null 表示全局域；
         * ORG/POSITION 必填（由服务端跨字段业务校验保证）。
         */
        String domainCode,
        /**
         * 角色类型码
         */
        @NotBlank String roleTypeCode,
        /**
         * 角色外部ID
         */
        @NotBlank String roleExternalId,
        /**
         * 关联关系ID（可选）
         */
        Long relationId
    ) {}
}
