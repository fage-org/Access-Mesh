package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.Oauth2ClientCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.Oauth2ClientPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.Oauth2ClientUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.Oauth2ClientResp;
import cn.ac.fage.accessmesh.access.admin.service.Oauth2ClientService;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2客户端管理控制器
 * <p>
 * 提供OAuth2客户端的CRUD操作和分页查询功能。
 * OAuth2客户端是接入系统的第三方应用，需要注册客户端ID和密钥。
 * 客户端配置包括授权类型、回调地址、授权范围等信息。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/oauth2/client")
public class Oauth2ClientController {

    private final Oauth2ClientService oauth2ClientService;

    /**
     * 构造函数注入依赖
     *
     * @param oauth2ClientService OAuth2客户端管理服务
     */
    public Oauth2ClientController(Oauth2ClientService oauth2ClientService) {
        this.oauth2ClientService = oauth2ClientService;
    }

    /**
     * 创建OAuth2客户端
     * <p>
     * 注册新的OAuth2客户端应用，设置客户端ID、密钥、回调地址等。
     * 客户端注册后才能通过OAuth2协议接入系统。
     * </p>
     *
     * @param req 客户端创建请求，包含客户端基本信息
     * @return 创建成功的客户端ID
     */
    @PostMapping("/create")
    public R<Long> createClient(@Valid @RequestBody Oauth2ClientCreateReq req) {
        return R.ok(oauth2ClientService.createClient(req));
    }

    /**
     * 更新OAuth2客户端配置
     * <p>
     * 更新客户端的名称、回调地址、授权范围等属性。
     * </p>
     *
     * @param req 客户端更新请求，包含客户端ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/update")
    public R<Void> updateClient(@Valid @RequestBody Oauth2ClientUpdateReq req) {
        oauth2ClientService.updateClient(req);
        return R.ok();
    }

    /**
     * 删除OAuth2客户端
     * <p>
     * 批量删除OAuth2客户端，会同时处理客户端的授权记录。
     * </p>
     *
     * @param req ID集合请求，包含待删除的客户端ID列表
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    public R<Void> deleteClients(@Valid @RequestBody IdsReq req) {
        oauth2ClientService.deleteClients(req);
        return R.ok();
    }

    /**
     * 获取OAuth2客户端详情
     * <p>
     * 根据客户端ID查询客户端的完整信息。
     * </p>
     *
     * @param req ID请求，包含客户端ID
     * @return 客户端详情信息
     */
    @PostMapping("/detail")
    public R<Oauth2ClientResp> getClient(@Valid @RequestBody IdReq req) {
        return R.ok(oauth2ClientService.getClientResp(req.id()));
    }

    /**
     * 分页查询OAuth2客户端列表
     * <p>
     * 查询系统中注册的OAuth2客户端列表，支持分页和过滤。
     * </p>
     *
     * @param req 分页查询请求，包含分页参数和过滤条件
     * @return 分页客户端列表结果
     */
    @PostMapping("/page")
    public R<PageResp<Oauth2ClientResp>> pageClients(@Valid @RequestBody Oauth2ClientPageReq req) {
        return R.ok(oauth2ClientService.pageClientResps(req));
    }
}