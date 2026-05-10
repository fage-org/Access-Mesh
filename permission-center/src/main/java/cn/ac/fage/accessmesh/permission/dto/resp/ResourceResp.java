package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 资源响应体
 * <p>
 * 返回资源实体的详细信息，包括类型、编码、名称、路径等。
 * 用于资源查询接口的响应。
 * </p>
 *
 * @param id               资源ID
 * @param tenantId         租户ID
 * @param bizDomainId      业务域ID
 * @param parentId         父资源ID
 * @param resourceTypeCode 资源类型编码
 * @param resourceTypeName 资源类型名称
 * @param code             资源编码，唯一标识
 * @param codeType         编码类型
 * @param name             资源名称，用于显示
 * @param path             资源路径
 * @param status           资源状态，0=禁用，1=启用
 * @param sortOrder        排序顺序
 * @param extra            扩展属性JSON
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record ResourceResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    Long parentId,
    String resourceTypeCode,
    String resourceTypeName,
    String code,
    String codeType,
    String name,
    String path,
    Integer status,
    Integer sortOrder,
    String extra,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}