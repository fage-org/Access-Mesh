package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ChangeLogListReq;
import cn.ac.fage.accessmesh.permission.dto.req.OperationLogListReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationLogResp;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
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
    public PermResult<List<ChangeLogResp>> listChangeLogs(@Valid @RequestBody ChangeLogListReq req) {
        return PermResult.success(advancedFeatureService.listChangeLogs(
                TenantContextHolder.getTenantId(), req.entityType(), req.entityId(),
                req.pageNum() != null ? req.pageNum() : 0,
                req.pageSize() != null ? req.pageSize() : 20));
    }
}
