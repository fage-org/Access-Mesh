package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 资源依赖关系响应体
 * <p>
 * 返回资源依赖关系的详细信息，包括源资源、目标资源、操作要求等。
 * 用于资源依赖查询接口的响应。
 * </p>
 *
 * @param id                    依赖关系ID
 * @param tenantId              租户ID
 * @param resourceEntityId      源资源实体ID
 * @param sourceResourceCode    源资源编码
 * @param dependsOnResourceEntityId 目标资源实体ID
 * @param depResourceCode       目标资源编码
 * @param sourceOperationBits   源操作权限位
 * @param requiredOperationBits 要求的操作权限位
 * @param autoGrant             是否自动授权
 * @param description           依赖关系描述
 * @param createdAt             创建时间
 */
public record ResourceDependencyResp(
    Long id,
    Long tenantId,
    Long resourceEntityId,
    String sourceResourceCode,
    Long dependsOnResourceEntityId,
    String depResourceCode,
    Long sourceOperationBits,
    Long requiredOperationBits,
    Boolean autoGrant,
    String description,
    LocalDateTime createdAt
) {}