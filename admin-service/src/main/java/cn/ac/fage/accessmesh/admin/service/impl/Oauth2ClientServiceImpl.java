package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.Oauth2ClientResp;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.Oauth2ClientService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.dev33.satoken.secure.BCrypt;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT;

@Service
public class Oauth2ClientServiceImpl implements Oauth2ClientService {

    private final SysOauth2ClientMapper oauth2ClientMapper;
    private final AdminPermissionValidator permissionValidator;

    public Oauth2ClientServiceImpl(SysOauth2ClientMapper oauth2ClientMapper,
                                   AdminPermissionValidator permissionValidator) {
        this.oauth2ClientMapper = oauth2ClientMapper;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createClient(Oauth2ClientCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.OAUTH2_CLIENT, AdminOperationCode.CREATE);

        // 检查clientId是否已存在
        SysOauth2Client existing = oauth2ClientMapper.selectOneByQuery(
            QueryWrapper.create().where(SYS_OAUTH2_CLIENT.CLIENT_ID.eq(req.clientId())).and(SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0))
        );
        if (existing != null) {
            throw new BizException(AdminErrorCode.CLIENT_ID_EXISTS.getCode(), AdminErrorCode.CLIENT_ID_EXISTS.getMessage());
        }

        SysOauth2Client client = new SysOauth2Client();
        client.setClientId(req.clientId());
        // clientSecret 应加密存储
        client.setClientSecret(BCrypt.hashpw(req.clientSecret()));
        client.setClientName(req.clientName());
        client.setGrantTypes(req.grantTypes());
        client.setRedirectUris(req.redirectUris());
        client.setScopes(req.scopes());
        client.setAccessTokenTtl(req.accessTokenTtl() != null ? req.accessTokenTtl() : 3600);
        client.setRefreshTokenTtl(req.refreshTokenTtl() != null ? req.refreshTokenTtl() : 86400);
        client.setStatus(req.status() != null ? req.status() : 1);
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        client.setDeleteFlag(0L);

        oauth2ClientMapper.insert(client);
        return client.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClient(Oauth2ClientUpdateReq req) {
        // Permission check - instance-level UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.OAUTH2_CLIENT,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        SysOauth2Client existing = oauth2ClientMapper.selectOneById(req.id());
        if (existing == null || existing.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.CLIENT_NOT_FOUND.getCode(), "OAuth2客户端不存在");
        }

        // 只更新非null字段
        if (req.clientSecret() != null) {
            existing.setClientSecret(BCrypt.hashpw(req.clientSecret()));
        }
        if (req.clientName() != null) {
            existing.setClientName(req.clientName());
        }
        if (req.grantTypes() != null) {
            existing.setGrantTypes(req.grantTypes());
        }
        if (req.redirectUris() != null) {
            existing.setRedirectUris(req.redirectUris());
        }
        if (req.scopes() != null) {
            existing.setScopes(req.scopes());
        }
        if (req.accessTokenTtl() != null) {
            existing.setAccessTokenTtl(req.accessTokenTtl());
        }
        if (req.refreshTokenTtl() != null) {
            existing.setRefreshTokenTtl(req.refreshTokenTtl());
        }
        if (req.status() != null) {
            existing.setStatus(req.status());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        oauth2ClientMapper.update(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClients(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.OAUTH2_CLIENT, resourceCodes, AdminOperationCode.DELETE);

        // Batch soft delete (performance fix: use single SQL instead of loop)
        LocalDateTime now = LocalDateTime.now();
        oauth2ClientMapper.softDeleteBatch(req.ids(), now);
    }

    @Override
    public SysOauth2Client getClientEntity(Long id) {
        return oauth2ClientMapper.selectOneById(id);
    }

    @Override
    public Oauth2ClientResp getClientResp(Long id) {
        SysOauth2Client entity = oauth2ClientMapper.selectOneById(id);
        if (entity == null || entity.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.CLIENT_NOT_FOUND.getCode(), "OAuth2客户端不存在");
        }
        return Oauth2ClientResp.fromEntity(entity);
    }

    @Override
    public SysOauth2Client getClientByClientId(String clientId) {
        return oauth2ClientMapper.selectOneByQuery(
            QueryWrapper.create().where(SYS_OAUTH2_CLIENT.CLIENT_ID.eq(clientId)).and(SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public PaginatedResult<Oauth2ClientResp> pageClientResps(Oauth2ClientPageReq req) {
        QueryWrapper qw = QueryWrapper.create().where(SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0));

        if (req.clientName() != null) {
            qw.and(SYS_OAUTH2_CLIENT.CLIENT_NAME.like(req.clientName()));
        }
        if (req.status() != null) {
            qw.and(SYS_OAUTH2_CLIENT.STATUS.eq(req.status()));
        }
        qw.orderBy(SYS_OAUTH2_CLIENT.CREATED_AT.desc());

        Page<SysOauth2Client> page = Page.of(req.getPageNum(), req.getPageSize());
        Page<SysOauth2Client> result = oauth2ClientMapper.paginate(page, qw);

        List<Oauth2ClientResp> items = result.getRecords().stream()
            .map(Oauth2ClientResp::fromEntity)
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + req.getPageSize() - 1) / req.getPageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), req.getPageNum(), req.getPageSize(), (int) totalPages));
    }
}