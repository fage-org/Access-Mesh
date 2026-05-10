package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 用户响应体
 * <p>
 * 返回抽象用户的详细信息，包括类型、外部标识、名称等。
 * 用于用户查询接口的响应。
 * </p>
 *
 * @param id              用户ID
 * @param tenantId        租户ID
 * @param subjectTypeCode 用户类型编码
 * @param externalId      外部标识，用于与外部系统关联
 * @param name            用户名称
 * @param enabled         是否启用
 * @param extra           扩展属性JSON
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record UserResp(
    Long id,
    Long tenantId,
    String subjectTypeCode,
    String externalId,
    String name,
    Boolean enabled,
    String extra,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}