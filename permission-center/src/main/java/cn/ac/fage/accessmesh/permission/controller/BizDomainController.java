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
 * Business domain management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/biz-domain")
public class BizDomainController {

    private final ConfigManageService configManageService;

    public BizDomainController(ConfigManageService configManageService) {
        this.configManageService = configManageService;
    }

    @PostMapping("/create")
    public PermResult<BizDomainResp> createBizDomain(@Valid @RequestBody BizDomainCreateReq req) {
        return PermResult.success(configManageService.createBizDomain(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<BizDomainResp> getBizDomain(@Valid @RequestBody IdReq req) {
        return PermResult.success(configManageService.getBizDomain(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<BizDomainResp>> listBizDomains(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            configManageService.listBizDomains(TenantContextHolder.getTenantId())
        ));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteBizDomain(@Valid @RequestBody IdsReq req) {
        configManageService.deleteBizDomainsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/update")
    public PermResult<BizDomainResp> updateBizDomain(@Valid @RequestBody BizDomainUpdateReq req) {
        return PermResult.success(configManageService.updateBizDomain(TenantContextHolder.getTenantId(), req, null));
    }
}
