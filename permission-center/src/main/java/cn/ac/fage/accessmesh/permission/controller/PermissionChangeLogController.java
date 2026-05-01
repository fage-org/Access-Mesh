package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ChangeLogListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import cn.ac.fage.accessmesh.permission.util.PageUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Permission change log query API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/permission-change-log")
public class PermissionChangeLogController {

    private final AdvancedFeatureService advancedFeatureService;

    public PermissionChangeLogController(AdvancedFeatureService advancedFeatureService) {
        this.advancedFeatureService = advancedFeatureService;
    }

    @PostMapping("/list")
    public PermResult<PaginatedResp<ChangeLogResp>> listChangeLogs(@Valid @RequestBody ChangeLogListReq req) {
        int pageNum = PageUtil.pageNum(req.pageNum());
        int pageSize = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(pageNum, pageSize);
        Long tenantId = TenantContextHolder.getTenantId();
        long total = advancedFeatureService.countChangeLogs(tenantId, req.entityType(), req.entityId());
        List<ChangeLogResp> items = advancedFeatureService.listChangeLogs(
                tenantId, req.entityType(), req.entityId(),
                offset, pageSize);
        return PermResult.success(new PaginatedResp<>(items, total, pageNum, pageSize, PageUtil.hasNext(offset, items.size(), total)));
    }
}
