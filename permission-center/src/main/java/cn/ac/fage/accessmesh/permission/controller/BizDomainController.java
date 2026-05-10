package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.permission.dto.resp.BizDomainResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.service.ConfigManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务域管理控制器
 * <p>
 * 提供业务域的CRUD操作和查询功能。
 * 业务域是权限系统的顶层分区概念，用于隔离不同业务场景的权限配置。
 * 例如：订单域、客户域、库存域等。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/api/perm/biz-domain")
public class BizDomainController {

    private final ConfigManageService configManageService;

    /**
     * 构造函数注入依赖
     *
     * @param configManageService 配置管理服务
     */
    public BizDomainController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    /**
     * 创建业务域
     * <p>
     * 创建新的业务域，用于隔离特定业务场景的权限配置。
     * </p>
     *
     * @param req 业务域创建请求，包含域编码、名称、描述等
     * @return 创建成功的业务域详情
     */
    @PostMapping("/create")
    public PermResult<BizDomainResp> createBizDomain(@Valid @RequestBody BizDomainCreateReq req) {
        return PermResult.success(configManageService.createBizDomain(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 获取业务域详情
     * <p>
     * 根据业务域ID查询域的完整信息。
     * </p>
     *
     * @param req ID请求，包含业务域ID
     * @return 业务域详情信息
     */
    @PostMapping("/detail")
    public PermResult<BizDomainResp> getBizDomain(@Valid @RequestBody IdReq req) {
        return PermResult.success(configManageService.getBizDomain(TenantContextHolder.getTenantId(), req.id()));
    }

    /**
     * 查询业务域列表
     * <p>
     * 返回租户下所有的业务域列表，无过滤条件。
     * </p>
     *
     * @param req 空请求，用于保持接口一致性
     * @return 业务域列表
     */
    @PostMapping("/list")
    public PermResult<ItemsResp<BizDomainResp>> listBizDomains(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listBizDomains(TenantContextHolder.getTenantId())
        ));
    }

    /**
     * 删除业务域
     * <p>
     * 批量删除业务域，会同时处理域下的资源配置。
     * </p>
     *
     * @param req ID集合请求，包含待删除的业务域ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteBizDomain(@Valid @RequestBody IdsReq req) {
        configManageService.deleteBizDomainsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    /**
     * 更新业务域信息
     * <p>
     * 更新业务域的名称、编码、描述等属性。
     * </p>
     *
     * @param req 业务域更新请求，包含业务域ID和新属性值
     * @return 更新后的业务域详情
     */
    @PostMapping("/update")
    public PermResult<BizDomainResp> updateBizDomain(@Valid @RequestBody BizDomainUpdateReq req) {
        return PermResult.success(configManageService.updateBizDomain(TenantContextHolder.getTenantId(), req, null));
    }
}