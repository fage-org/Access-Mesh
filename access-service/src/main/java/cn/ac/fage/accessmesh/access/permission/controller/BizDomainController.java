package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainDetailReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BizDomainResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.access.permission.service.BizDomainAppService;
import cn.ac.fage.accessmesh.access.permission.util.PageUtil;
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

    private final BizDomainAppService bizDomainAppService;

    /**
     * 构造函数注入依赖
     *
     * @param bizDomainAppService 业务域应用服务
     */
    public BizDomainController(BizDomainAppService bizDomainAppService) {
        this.bizDomainAppService = bizDomainAppService;
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
        return PermResult.success(bizDomainAppService.createBizDomain(TenantContextHolder.getTenantId(), req, null));
    }

    /**
     * 获取业务域详情
     * <p>
     * 按业务键 code 查询域的完整信息（T-PERM-026 切业务键，uk_biz_domain 保证租户内唯一）；
     * 未知编码返回 data=null 不抛错。
     * </p>
     *
     * @param req 详情请求，包含业务域编码
     * @return 业务域详情信息
     */
    @PostMapping("/detail")
    public PermResult<BizDomainResp> getBizDomain(@Valid @RequestBody BizDomainDetailReq req) {
        return PermResult.success(bizDomainAppService.getBizDomain(TenantContextHolder.getTenantId(), req.domainCode()));
    }

    /**
     * 查询业务域列表
     * <p>
     * 支持关键字过滤（code/name/description）与服务端分页（T-PERM-026 收口，system-config 同范式）。
     * pageNum/pageSize 均未传 = 字典全量（上限 PageUtil.MAX_PAGE_SIZE，先例 /role/list）。
     * </p>
     *
     * @param req 列表查询请求，含关键字与分页参数（均可选）
     * @return 分页业务域列表
     */
    @PostMapping("/list")
    public PermResult<PaginatedResp<BizDomainResp>> listBizDomains(@Valid @RequestBody BizDomainListReq req) {
        boolean paged = req.pageNum() != null || req.pageSize() != null;
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = paged ? PageUtil.pageSize(req.pageSize()) : PageUtil.MAX_PAGE_SIZE;
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = bizDomainAppService.countBizDomains(tenantId, req.keyword());
        List<BizDomainResp> items =
            bizDomainAppService.listBizDomains(tenantId, req.keyword(), offset, pageSize);
        return PermResult.success(new PaginatedResp<>(
            items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }

    /**
     * 删除业务域
     * <p>
     * 批量软删除业务域。删除保护：全局域不可删、域下存在有效域配置时拒删（20051）。
     * </p>
     *
     * @param req ID集合请求，包含待删除的业务域ID列表
     * @return 操作成功结果
     */
    @PostMapping("/remove")
    public PermResult<Void> deleteBizDomain(@Valid @RequestBody IdsReq req) {
        bizDomainAppService.deleteBizDomainsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
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
        return PermResult.success(bizDomainAppService.updateBizDomain(TenantContextHolder.getTenantId(), req, null));
    }
}