package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.model.permission.PermissionSnapshot;
import org.dromara.permission.model.permission.PermissionVersionQueryRequest;
import org.dromara.permission.model.permission.PermissionVersionResult;
import org.dromara.permission.model.permission.SnapshotRequest;
import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.RevokePermissionRequest;
import org.dromara.permission.domain.vo.PermissionGrantVo;
import org.dromara.permission.domain.vo.PermissionRevokeVo;
import org.dromara.permission.domain.vo.PermissionSnapshotEntryVo;
import org.dromara.permission.domain.vo.PermissionSnapshotVo;
import org.dromara.permission.domain.vo.PermissionCheckConflictVo;
import org.dromara.permission.domain.vo.PermissionVersionVo;
import org.dromara.permission.service.PermissionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/service")
public class PermissionServiceController {

    private final PermissionService permissionService;

    @PostMapping("/grant")
    public R<PermissionGrantVo> grant(@Validated @RequestBody GrantPermissionRequest request) {
        var result = permissionService.grant(request);
        PermissionGrantVo vo = new PermissionGrantVo();
        vo.setSuccess(result.isSuccess());
        vo.setPermissionId(result.getPermissionId());
        vo.setRejectReasons(result.getRejectReasons());
        return R.ok(vo);
    }

    @PostMapping("/revoke")
    public R<PermissionRevokeVo> revoke(@Validated @RequestBody RevokePermissionRequest request) {
        var result = permissionService.revoke(request);
        PermissionRevokeVo vo = new PermissionRevokeVo();
        vo.setSuccess(result.isSuccess());
        vo.setPermissionId(result.getPermissionId());
        return R.ok(vo);
    }

    @PostMapping("/snapshot")
    public R<PermissionSnapshotVo> snapshot(@Validated @RequestBody SnapshotRequest request) {
        PermissionSnapshot result = permissionService.buildSnapshot(request);
        PermissionSnapshotVo vo = new PermissionSnapshotVo();
        vo.setTenantId(result.getTenantId());
        vo.setAbstractUserId(result.getAbstractUserId());
        vo.setBizDomainId(result.getBizDomainId());
        vo.setVersionToken(result.getVersionToken());
        vo.setEntries(result.getEntries().stream().map(item -> {
            PermissionSnapshotEntryVo entryVo = new PermissionSnapshotEntryVo();
            entryVo.setRoleId(item.getRoleId());
            entryVo.setResourceId(item.getResourceId());
            entryVo.setResourceCode(item.getResourceCode());
            entryVo.setOperationId(item.getOperationId());
            entryVo.setOperationCode(item.getOperationCode());
            entryVo.setConditionId(item.getConditionId());
            entryVo.setCanManage(item.getCanManage());
            return entryVo;
        }).collect(Collectors.toList()));
        vo.setConflicts(result.getConflicts().stream().map(item -> {
            PermissionCheckConflictVo conflictVo = new PermissionCheckConflictVo();
            conflictVo.setConflictRuleId(item.getConflictRuleId());
            conflictVo.setResourceId(item.getResourceId());
            conflictVo.setFirstOperationId(item.getFirstOperationId());
            conflictVo.setSecondOperationId(item.getSecondOperationId());
            return conflictVo;
        }).collect(Collectors.toList()));
        return R.ok(vo);
    }

    @PostMapping("/version")
    public R<PermissionVersionVo> version(@Validated @RequestBody PermissionVersionQueryRequest request) {
        PermissionVersionResult result = permissionService.queryVersion(request);
        PermissionVersionVo vo = new PermissionVersionVo();
        vo.setTenantId(result.getTenantId());
        vo.setVersionNo(result.getVersionNo());
        vo.setVersionToken(result.getVersionToken());
        vo.setTriggerEntityType(result.getTriggerEntityType());
        vo.setTriggerEntityId(result.getTriggerEntityId());
        vo.setRemark(result.getRemark());
        return R.ok(vo);
    }
}
