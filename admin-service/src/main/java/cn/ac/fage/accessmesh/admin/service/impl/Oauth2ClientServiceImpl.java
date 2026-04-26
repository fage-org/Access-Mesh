package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.admin.entity.table.SysOauth2ClientTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOauth2ClientMapper;
import cn.ac.fage.accessmesh.admin.service.Oauth2ClientService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.ac.fage.accessmesh.admin.entity.table.SysOauth2ClientTableDef.SYS_OAUTH2_CLIENT;

@Service
public class Oauth2ClientServiceImpl implements Oauth2ClientService {

    private final SysOauth2ClientMapper oauth2ClientMapper;

    public Oauth2ClientServiceImpl(SysOauth2ClientMapper oauth2ClientMapper) {
        this.oauth2ClientMapper = oauth2ClientMapper;
    }

    @Override
    @Transactional
    public Long createClient(SysOauth2Client client) {
        SysOauth2Client existing = oauth2ClientMapper.selectOneByQuery(
            QueryWrapper.create().where(SYS_OAUTH2_CLIENT.CLIENT_ID.eq(client.getClientId())).and(SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0))
        );
        if (existing != null) {
            throw new BizException(AdminErrorCode.CLIENT_ID_EXISTS.getCode(), AdminErrorCode.CLIENT_ID_EXISTS.getMessage());
        }
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        client.setDeleteFlag(0L);
        client.setStatus(client.getStatus() != null ? client.getStatus() : 1);
        oauth2ClientMapper.insert(client);
        return client.getId();
    }

    @Override
    @Transactional
    public void updateClient(SysOauth2Client client) {
        SysOauth2Client existing = oauth2ClientMapper.selectOneById(client.getId());
        if (existing == null || existing.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.CLIENT_ID_EXISTS.getCode(), "OAuth2客户端不存在");
        }
        client.setUpdatedAt(LocalDateTime.now());
        oauth2ClientMapper.update(client);
    }

    @Override
    @Transactional
    public void deleteClients(IdsReq req) {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.ids()) {
            SysOauth2Client client = oauth2ClientMapper.selectOneById(id);
            if (client == null || client.getDeleteFlag() != 0L) continue;
            client.setDeleteFlag(1L);
            client.setDeletedAt(now);
            oauth2ClientMapper.update(client);
        }
    }

    @Override
    public SysOauth2Client getClient(Long id) {
        return oauth2ClientMapper.selectOneById(id);
    }

    @Override
    public SysOauth2Client getClientByClientId(String clientId) {
        return oauth2ClientMapper.selectOneByQuery(
            QueryWrapper.create().where(SYS_OAUTH2_CLIENT.CLIENT_ID.eq(clientId)).and(SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public PaginatedResult<SysOauth2Client> pageClients(PageReq pageReq) {
        Page<SysOauth2Client> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysOauth2Client> result = oauth2ClientMapper.paginate(page,
            QueryWrapper.create().where(SYS_OAUTH2_CLIENT.DELETE_FLAG.eq(0)).orderBy(SYS_OAUTH2_CLIENT.CREATED_AT.desc()));

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(result.getRecords(),
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }
}
