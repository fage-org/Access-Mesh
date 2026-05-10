package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 域配置响应体
 * <p>
 * 返回域配置的详细信息，包括配置类型、配置值等。
 * 用于域配置查询接口的响应。
 * </p>
 *
 * @param id          配置ID
 * @param tenantId    租户ID
 * @param bizDomainId 业务域ID
 * @param configType  配置类型编码
 * @param extra       配置值JSON
 * @param updatedAt   更新时间
 */
public record DomainConfigResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    String configType,
    String extra,
    LocalDateTime updatedAt
) {}