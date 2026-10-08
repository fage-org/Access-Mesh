package cn.ac.fage.accessmesh.access.auth.service.domain.impl;

import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.auth.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.access.auth.service.domain.OAuth2ClientDomainService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OAuth2客户端领域服务实现类
 * <p>
 * 封装OAuth2客户端的数据访问逻辑，提供按客户端ID查询、按主键查询等方法。
 * OAuth2客户端用于第三方应用接入，支持授权码流程。
 * 所有查询均带有删除标记过滤，确保数据安全。
 * client_id 租户内唯一（T-ACCESS-097）：按 clientId 解析必须带租户，
 * 匿名端点先取跨租户列表、租户行由凭据记录选出。
 * </p>
 */
@Service
public class OAuth2ClientDomainServiceImpl implements OAuth2ClientDomainService {

    private final SysOauth2ClientMapper oauth2ClientMapper;

    /**
     * 构造函数注入依赖
     *
     * @param oauth2ClientMapper OAuth2客户端数据访问层
     */
    public OAuth2ClientDomainServiceImpl(SysOauth2ClientMapper oauth2ClientMapper) {
        this.oauth2ClientMapper = oauth2ClientMapper;
    }

    /**
     * 查询有效的OAuth2客户端
     * <p>
     * 根据主键ID查询客户端，带租户隔离和删除标记过滤。
     * 用于客户端管理操作前的校验。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param id       客户端主键ID
     * @return OAuth2客户端实体，不存在返回null
     */
    @Override
    public SysOauth2Client selectValidById(Long tenantId, Long id) {
        if (id == null) {
            return null;
        }
        return oauth2ClientMapper.selectByIdSafe(tenantId, id);
    }

    /**
     * 查询启用状态的OAuth2客户端（租户内唯一键定位）
     * <p>
     * 根据租户与客户端ID查询已启用且未删除的客户端，用于OAuth2授权流程中
     * 验证客户端是否可使用（T-ACCESS-097：租户内唯一，解析必须带租户）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param clientId 客户端ID（OAuth2标识）
     * @return OAuth2客户端实体，不存在或未启用返回null
     */
    @Override
    public SysOauth2Client findActiveByClientId(Long tenantId, String clientId) {
        if (tenantId == null || clientId == null || clientId.isBlank()) {
            return null;
        }
        return oauth2ClientMapper.selectActiveByClientId(tenantId, clientId);
    }

    /**
     * 按客户端ID查询全部租户的启用客户端列表（跨租户同名定位）
     * <p>
     * 匿名 token/refresh 端点在读取授权码/刷新令牌记录前无法确定租户，
     * 先按 clientId 取跨租户启用列表做存在性判定（T-ACCESS-097）。
     * </p>
     *
     * @param clientId 客户端ID（OAuth2标识）
     * @return 启用客户端列表（跨租户同名行），无匹配返回空列表
     */
    @Override
    public List<SysOauth2Client> findActiveListByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return List.of();
        }
        return oauth2ClientMapper.selectActiveListByClientId(clientId);
    }
}
