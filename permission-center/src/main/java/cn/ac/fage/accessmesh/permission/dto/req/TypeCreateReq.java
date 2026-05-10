package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 类型定义创建请求体
 * <p>
 * 用于创建新的类型定义，包括类型键、类型值、名称等。
 * </p>
 *
 * @param bizDomainId  业务域ID，可选
 * @param typeKey      类型键，必填，用于分类
 * @param typeValue    类型值，必填，对应数据库存储值
 * @param name         类型名称，必填
 * @param description  类型描述，可选
 * @param isSystem     是否系统预置类型，可选
 * @param sortOrder    排序顺序，可选
 * @param extra        扩展属性JSON，可选
 */
public record TypeCreateReq(
    Long bizDomainId,
    @NotBlank String typeKey,
    @NotNull Integer typeValue,
    @NotBlank String name,
    String description,
    Boolean isSystem,
    Integer sortOrder,
    String extra
) {}