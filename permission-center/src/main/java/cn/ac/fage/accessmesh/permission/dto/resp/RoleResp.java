package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 角色响应体
 * <p>
 * 返回抽象角色的详细信息，包括类型、名称、状态等。
 * 用于角色查询接口的响应。
 * </p>
 *
 * @param id           角色ID
 * @param tenantId     租户ID
 * @param parentId     父角色ID
 * @param roleTypeCode 角色类型编码
 * @param roleTypeName 角色类型名称
 * @param externalId   外部标识，用于与外部系统关联
 * @param name         角色名称
 * @param status       角色状态，0=禁用，1=启用
 * @param sortOrder    排序顺序
 * @param extra        扩展属性JSON
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record RoleResp(
    Long id,
    Long tenantId,
    Long parentId,
    String roleTypeCode,
    String roleTypeName,
    String externalId,
    String name,
    Integer status,
    Integer sortOrder,
    String extra,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}