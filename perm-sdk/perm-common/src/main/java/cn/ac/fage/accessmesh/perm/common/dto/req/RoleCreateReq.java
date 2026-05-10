package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 角色创建请求
 * <p>
 * 用于在权限中心创建角色的共享请求对象。
 * </p>
 */
public record RoleCreateReq(
    /**
     * 业务域ID
     */
    Long bizDomainId,
    /**
     * 父角色ID
     */
    Long parentId,
    /**
     * 角色类型码
     */
    @NotBlank(message = "角色类型不能为空") String roleTypeCode,
    /**
     * 外部ID
     */
    String externalId,
    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空") String name,
    /**
     * 排序号
     */
    Integer sortOrder,
    /**
     * 扩展信息
     */
    String extra
) {}
