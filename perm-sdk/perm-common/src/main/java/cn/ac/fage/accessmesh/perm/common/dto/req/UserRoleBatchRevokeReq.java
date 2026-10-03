package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
    @NotEmpty @Size(max = 1000, message = "批量上限 1000（project-rules §分批约束，超限分批提交）")
    @Valid List<@NotNull RevokeItem> items
) {
    /**
     * 单个撤销项
     * <p>
     * 定义单个用户角色关联的撤销请求。类型码/域码三标识字段带 @Pattern
     * （T-PERM-104，Q-057 碰撞收口，与 UserAssignRoleReq.AssignItem 同款——
     * revoke 链路与 assign 同一组分组/回读键，须同批收口）。
     * </p>
     */
    public record RevokeItem(
        /**
         * 主体类型码
         */
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "主体类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
        String subjectTypeCode,
        /**
         * 主体外部ID
         */
        @NotBlank String subjectExternalId,
        /**
         * 业务域码：功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）允许 null 表示全局域；
         * ORG/POSITION 必填（由服务端跨字段业务校验保证）。
         * 空串不合法（须传 null 表示全局域，T-API-004 空串拒先例）。
         */
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "业务域编码必须以大写字母开头，仅含大写字母/数字/下划线")
        String domainCode,
        /**
         * 角色类型码
         */
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色类型编码必须以大写字母开头，仅含大写字母/数字/下划线")
        String roleTypeCode,
        /**
         * 角色外部ID
         */
        @NotBlank String roleExternalId,
        /**
         * 关联关系ID（可选，须为正整数——与 null 在 uk_user_role 同槽位，0 拒绝；
         * T-ADMIN-030 外评处置）
         */
        @Positive Long relationId
    ) {}
}
