package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.Oauth2ClientResp;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

public interface Oauth2ClientService {

    /**
     * 创建OAuth2客户端
     */
    Long createClient(Oauth2ClientCreateReq req);

    /**
     * 更新OAuth2客户端
     */
    void updateClient(Oauth2ClientUpdateReq req);

    /**
     * 删除OAuth2客户端
     */
    void deleteClients(IdsReq req);

    /**
     * 获取OAuth2客户端详情（返回实体，用于内部调用）
     */
    SysOauth2Client getClientEntity(Long id);

    /**
     * 获取OAuth2客户端详情（返回DTO，排除敏感字段）
     */
    Oauth2ClientResp getClientResp(Long id);

    /**
     * 根据clientId获取客户端（用于OAuth2认证，需要clientSecret）
     */
    SysOauth2Client getClientByClientId(String clientId);

    /**
     * 分页查询OAuth2客户端（返回DTO，排除敏感字段）
     */
    PaginatedResult<Oauth2ClientResp> pageClientResps(Oauth2ClientPageReq req);
}