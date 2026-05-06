package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;

/**
 * OAuth2客户端领域服务
 * 封装OAuth2客户端查询核心领域逻辑
 */
public interface OAuth2ClientDomainService {

    /**
     * 根据clientId查询有效的客户端
     *
     * @param tenantId 租户ID（可为null，表示不限定租户）
     * @param clientId 客户端ID
     * @return 客户端实体，不存在返回null
     */
    SysOauth2Client findByClientId(Long tenantId, String clientId);

    /**
     * 查询有效的客户端（未删除、属于指定租户）
     *
     * @param tenantId 租户ID
     * @param id       客户端ID
     * @return 客户端实体，不存在返回null
     */
    SysOauth2Client selectValidById(Long tenantId, Long id);

    /**
     * 根据clientId查询有效的启用状态客户端（用于OAuth2认证）
     *
     * @param clientId 客户端ID
     * @return 客户端实体，不存在或状态非启用返回null
     */
    SysOauth2Client findActiveByClientId(String clientId);
}