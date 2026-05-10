package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 类型定义响应体
 * <p>
 * 返回类型定义的详细信息，包括类型键、编码、值等。
 * 用于类型定义查询接口的响应。
 * </p>
 *
 * @param id          类型定义ID
 * @param tenantId    租户ID
 * @param bizDomainId 业务域ID
 * @param typeKey     类型键，用于分类
 * @param typeCode    类型编码，唯一标识
 * @param typeValue   类型值，对应数据库存储值
 * @param name        类型名称，用于显示
 * @param description 类型描述
 * @param isSystem    是否系统预置类型
 * @param sortOrder   排序顺序
 * @param extra       扩展属性JSON
 * @param createdAt   创建时间
 */
public record TypeDefinitionResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    String typeKey,
    String typeCode,
    Integer typeValue,
    String name,
    String description,
    Boolean isSystem,
    Integer sortOrder,
    String extra,
    LocalDateTime createdAt
) {}