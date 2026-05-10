package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.Oauth2ClientUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.Oauth2ClientResp;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

/**
 * OAuth2客户端服务接口
 * <p>
 * 提供OAuth2客户端的管理功能。
 * OAuth2客户端用于第三方应用接入，
 * 支持授权码模式、客户端凭证模式等OAuth2授权流程。
 * </p>
 */
public interface Oauth2ClientService {

    /**
     * 创建OAuth2客户端
     * <p>
     * 创建新的OAuth2客户端配置。
     * 包括客户端ID、密钥、授权类型、回调地址等。
     * </p>
     *
     * @param req OAuth2客户端创建请求
     * @return 创建的客户端ID
     */
    Long createClient(Oauth2ClientCreateReq req);

    /**
     * 更新OAuth2客户端
     * <p>
     * 更新指定OAuth2客户端的配置信息。
     * </p>
     *
     * @param req OAuth2客户端更新请求
     */
    void updateClient(Oauth2ClientUpdateReq req);

    /**
     * 删除OAuth2客户端
     * <p>
     * 批量删除多个OAuth2客户端配置。
     * </p>
     *
     * @param req 待删除的客户端ID列表请求
     */
    void deleteClients(IdsReq req);

    /**
     * 获取OAuth2客户端详情（返回实体）
     * <p>
     * 根据ID查询客户端完整信息，包括敏感字段（如clientSecret）。
     * 用于内部服务调用，不对外暴露。
     * </p>
     *
     * @param id 客户端ID
     * @return OAuth2客户端实体
     */
    SysOauth2Client getClientEntity(Long id);

    /**
     * 获取OAuth2客户端详情（返回DTO）
     * <p>
     * 根据ID查询客户端信息，排除敏感字段。
     * 用于对外接口响应。
     * </p>
     *
     * @param id 客户端ID
     * @return OAuth2客户端响应DTO
     */
    Oauth2ClientResp getClientResp(Long id);

    /**
     * 根据clientId获取客户端
     * <p>
     * 根据OAuth2 clientId查询客户端配置。
     * 用于OAuth2认证流程，需要返回clientSecret进行验证。
     * </p>
     *
     * @param clientId OAuth2客户端标识
     * @return OAuth2客户端实体
     */
    SysOauth2Client getClientByClientId(String clientId);

    /**
     * 分页查询OAuth2客户端
     * <p>
     * 查询系统中的OAuth2客户端列表，排除敏感字段。
     * 支持分页和条件过滤。
     * </p>
     *
     * @param req 分页查询请求
     * @return 分页OAuth2客户端响应结果
     */
    PaginatedResult<Oauth2ClientResp> pageClientResps(Oauth2ClientPageReq req);
}