package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.admin.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OAuth2ClientDomainService;
import org.springframework.stereotype.Service;

/**
 * OAuth2客户端领域服务实现类
 * <p>
 * 封装OAuth2客户端的数据访问逻辑，提供按客户端ID查询、按主键查询等方法。
 * OAuth2客户端用于第三方应用接入，支持授权码流程。
 * 所有查询均带有删除标记过滤，确保数据安全。
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
     * 查询启用状态的OAuth2客户端
     * <p>
     * 根据客户端ID查询已启用且未删除的客户端。
     * 用于OAuth2授权流程中验证客户端是否可使用。
     * 不限制租户（跨租户的公共客户端可能需要此查询）。
     * </p>
     *
     * @param clientId 客户端ID（OAuth2标识）
     * @return OAuth2客户端实体，不存在或未启用返回null
     */
    @Override
    public SysOauth2Client findActiveByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return oauth2ClientMapper.selectActiveByClientId(clientId);
    }
}
