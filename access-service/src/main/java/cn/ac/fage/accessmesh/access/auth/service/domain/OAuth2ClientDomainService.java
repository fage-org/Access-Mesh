package cn.ac.fage.accessmesh.access.auth.service.domain;

import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;

import java.util.List;

/**
 * OAuth2客户端领域服务
 * 封装OAuth2客户端查询核心领域逻辑
 */
public interface OAuth2ClientDomainService {

    /**
     * 查询有效的客户端（未删除、属于指定租户）
     *
     * @param tenantId 租户ID
     * @param id       客户端ID
     * @return 客户端实体，不存在返回null
     */
    SysOauth2Client selectValidById(Long tenantId, Long id);

    /**
     * 根据租户与clientId查询有效的启用状态客户端（用于OAuth2认证）
     * <p>
     * T-ACCESS-097：client_id 租户内唯一（跨租户可同名），解析必须带租户。
     * authorize（会话租户）与 OAuth2 JWT 校验（载荷 tenant_id claim）调用方
     * 在解析前已知租户。
     *
     * @param tenantId 租户ID
     * @param clientId 客户端ID
     * @return 客户端实体，不存在或状态非启用返回null
     */
    SysOauth2Client findActiveByClientId(Long tenantId, String clientId);

    /**
     * 按clientId查询全部租户的启用客户端列表（跨租户同名定位）
     * <p>
     * T-ACCESS-097：匿名 token/refresh 端点在读取授权码/刷新令牌记录前无法
     * 确定租户，先按 clientId 取跨租户启用列表做存在性判定，租户行由凭据
     * 记录选出。
     *
     * @param clientId 客户端ID
     * @return 启用客户端列表（跨租户同名行），无匹配返回空列表
     */
    List<SysOauth2Client> findActiveListByClientId(String clientId);
}
