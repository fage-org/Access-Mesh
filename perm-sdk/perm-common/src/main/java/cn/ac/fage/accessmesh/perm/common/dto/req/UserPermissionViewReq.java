package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 用户权限视图查询请求
 * <p>
 * 用于查询用户权限视图的共享请求对象。
 * 支持多种过滤条件和分页参数。
 * </p>
 */
public record UserPermissionViewReq(
    /**
     * 目标类型
     */
    @NotBlank String targetType,
    /**
     * 主体类型码
     */
    @NotBlank String subjectTypeCode,
    /**
     * 主体外部ID
     */
    @NotBlank String subjectExternalId,
    /**
     * 业务域码（可选）
     */
    String domainCode,
    /**
     * 角色类型码（可选）
     */
    String roleTypeCode,
    /**
     * 角色外部ID（可选）
     */
    String roleExternalId,
    /**
     * 资源类型码列表（可选）
     */
    List<String> resourceTypeCodes,
    /**
     * 操作码列表（可选）
     */
    List<String> operationCodes,
    /**
     * 资源关键字（可选）
     */
    String resourceKeyword,
    /**
     * 来源角色外部ID（可选）
     */
    String sourceRoleExternalId,
    /**
     * 是否包含范围信息
     */
    Boolean includeScopes,
    /**
     * 是否包含API资源
     */
    Boolean includeApiResources,
    /**
     * 是否包含来源角色
     */
    Boolean includeSourceRoles,
    /**
     * 来源角色数量限制
     */
    Integer sourceRoleLimit,
    /**
     * 页码
     */
    Integer pageNum,
    /**
     * 每页大小
     */
    Integer pageSize
) {}
