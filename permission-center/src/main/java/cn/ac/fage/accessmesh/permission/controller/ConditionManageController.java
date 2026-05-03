package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.EmptyReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.service.ConditionManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permission condition management API.
 */
@RestController
@RequestMapping("/api/perm/permission-condition")
public class ConditionManageController {

    private final ConditionManageService conditionManageService;

    public ConditionManageController(ConditionManageService conditionManageService) {
        this.conditionManageService = conditionManageService;
    }

    @PostMapping("/create")
    public PermResult<ConditionResp> createCondition(@Valid @RequestBody ConditionCreateReq req) {
        return PermResult.success(conditionManageService.createCondition(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<ConditionResp> getCondition(@Valid @RequestBody IdReq req) {
        return PermResult.success(conditionManageService.getCondition(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<ConditionResp>> listConditions(@Valid @RequestBody EmptyReq req) {
        return PermResult.success(new ItemsResp<>(
            conditionManageService.listConditions(TenantContextHolder.getTenantId())
        ));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteCondition(@Valid @RequestBody IdsReq req) {
        conditionManageService.deleteConditionsByIds(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/update")
    public PermResult<ConditionResp> updateCondition(@Valid @RequestBody ConditionUpdateReq req) {
        return PermResult.success(conditionManageService.updateCondition(TenantContextHolder.getTenantId(), req, null));
    }
}