package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 角色创建请求体
 * <p>
 * 用于创建新的抽象角色，包括业务域、父角色、类型和名称。
 * </p>
 *
 * @param bizDomainId  业务域ID，可选
 * @param parentId     父角色ID，可选
 * @param roleTypeCode 角色类型编码，必填
 * @param externalId   外部标识，可选，用于与外部系统关联
 * @param name         角色名称，必填
 * @param sortOrder    排序顺序，可选
 * @param extra        扩展属性JSON，可选
 */
public record RoleCreateReq(
    Long bizDomainId,
    Long parentId,
    @NotBlank(message = "角色类型不能为空")
    String roleTypeCode,
    String externalId,
    @NotBlank(message = "角色名称不能为空")
    String name,
    Integer sortOrder,
    String extra
) {}