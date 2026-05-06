package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.admin.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OAuth2ClientDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import cn.ac.fage.accessmesh.admin.entity.table.SysOauth2ClientTableDef;

@Service
public class OAuth2ClientDomainServiceImpl implements OAuth2ClientDomainService {

    private final SysOauth2ClientMapper oauth2ClientMapper;

    public OAuth2ClientDomainServiceImpl(SysOauth2ClientMapper oauth2ClientMapper) {
        this.oauth2ClientMapper = oauth2ClientMapper;
    }

    @Override
    public SysOauth2Client findByClientId(Long tenantId, String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        QueryWrapper qw = QueryWrapper.create()
            .where(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.CLIENT_ID.eq(clientId))
            .and(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0));
        if (tenantId != null) {
            qw.and(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.TENANT_ID.eq(tenantId));
        }
        return oauth2ClientMapper.selectOneByQuery(qw);
    }

    @Override
    public SysOauth2Client selectValidById(Long tenantId, Long id) {
        if (id == null) {
            return null;
        }
        return oauth2ClientMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.ID.eq(id))
                .and(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.TENANT_ID.eq(tenantId))
                .and(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public SysOauth2Client findActiveByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return oauth2ClientMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.CLIENT_ID.eq(clientId))
                .and(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.STATUS.eq(1))
                .and(SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0))
        );
    }
}