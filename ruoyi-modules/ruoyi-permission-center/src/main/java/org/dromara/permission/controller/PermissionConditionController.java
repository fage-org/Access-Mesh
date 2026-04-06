package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.ConditionListReq;
import org.dromara.permission.domain.dto.ConditionSaveReq;
import org.dromara.permission.domain.dto.ConditionUpdateReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.PermissionConditionVo;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.PermissionConditionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生效条件 permission_condition 接口
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/conditions")
public class PermissionConditionController {

    private final PermissionConditionService permissionConditionService;

    @GetMapping
    public R<List<PermissionConditionVo>> list(@Validated ConditionListReq req) {
        return R.ok(permissionConditionService.list(req));
    }

    @PostMapping
    public R<Void> create(@Validated @RequestBody ConditionSaveReq req) {
        req.setId(null);
        permissionConditionService.save(req);
        return R.ok();
    }

    @PutMapping("/{conditionId}")
    public R<Void> update(@PathVariable("conditionId") Long conditionId, @Validated @RequestBody ConditionUpdateReq req) {
        ensureConditionId(conditionId);
        permissionConditionService.update(conditionId, req);
        return R.ok();
    }

    @PostMapping("/list")
    public R<List<PermissionConditionVo>> listLegacy(@Validated @RequestBody ConditionListReq req) {
        return R.ok(permissionConditionService.list(req));
    }

    @PostMapping("/save")
    public R<Void> saveLegacy(@Validated @RequestBody ConditionSaveReq req) {
        permissionConditionService.save(req);
        return R.ok();
    }

    @PostMapping("/remove")
    public R<Void> removeLegacy(@Validated @RequestBody IdsReq req) {
        permissionConditionService.remove(req);
        return R.ok();
    }

    private void ensureConditionId(Long pathConditionId) {
        if (pathConditionId == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
    }
}
