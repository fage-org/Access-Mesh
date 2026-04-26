package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface Oauth2ClientService {

    Long createClient(SysOauth2Client client);

    void updateClient(SysOauth2Client client);

    void deleteClients(IdsReq req);

    SysOauth2Client getClient(Long id);

    SysOauth2Client getClientByClientId(String clientId);

    PaginatedResult<SysOauth2Client> pageClients(PageReq pageReq);
}
