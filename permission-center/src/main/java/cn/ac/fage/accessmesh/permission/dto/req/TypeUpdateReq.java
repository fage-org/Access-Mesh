package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 类型定义更新请求体
 * <p>
 * 用于更新类型定义的信息，包括名称、描述、排序等。
 * </p>
 *
 * @param typeId      类型定义ID，必填
 * @param bizDomainId 业务域ID，可选
 * @param name        类型名称，可选
 * @param description 类型描述，可选
 * @param sortOrder   排序顺序，可选
 * @param extra       扩展属性JSON，可选
 */
public record TypeUpdateReq(
    @NotNull Long typeId,
    Long bizDomainId,
    String name,
    String description,
    Integer sortOrder,
    String extra
) {}