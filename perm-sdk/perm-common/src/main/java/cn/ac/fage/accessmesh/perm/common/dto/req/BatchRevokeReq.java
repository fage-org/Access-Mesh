package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量撤销权限请求
 * <p>
 * 从角色批量撤销权限的共享请求对象。
 * </p>
 */
public record BatchRevokeReq(
    /**
     * 业务域码（可选）
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
     * 待撤销的权限ID列表
     */
    @NotEmpty List<Long> permissionIds
) {}
