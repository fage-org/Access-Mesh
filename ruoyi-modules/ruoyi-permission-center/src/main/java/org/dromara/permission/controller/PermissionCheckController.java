package org.dromara.permission.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.PermissionCheckReq;
import org.dromara.permission.domain.vo.PermissionCheckConflictVo;
import org.dromara.permission.domain.vo.PermissionCheckDependencyGapVo;
import org.dromara.permission.domain.vo.PermissionCheckGrantedByVo;
import org.dromara.permission.domain.vo.PermissionCheckVo;
import org.dromara.permission.model.permission.PermissionCheckRequest;
import org.dromara.permission.model.permission.PermissionCheckResult;
import org.dromara.permission.service.PermissionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm")
public class PermissionCheckController {

    private final PermissionService permissionService;

    @PostMapping("/check")
    public R<PermissionCheckVo> check(@Validated @RequestBody PermissionCheckReq req) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setTenantId(req.getTenantId());
        request.setAbstractUserId(req.getAbstractUserId());
        request.setResourceEntityId(req.getResourceEntityId());
        request.setOperationPermissionId(req.getOperationPermissionId());
        request.setBizDomainId(req.getBizDomainId());
        request.setInheritMode(req.getInheritMode());
        request.setCheckDependency(req.getCheckDependency());
        request.setContext(req.getContext() == null ? null : new java.util.HashMap<>(req.getContext()));
        PermissionCheckResult result = permissionService.check(request);
        return R.ok(new PermissionCheckVo(
            result.isGranted(),
            result.getDenyReason() == null ? null : result.getDenyReason().name(),
            toGrantedBy(result),
            toConflicts(result),
            toDependencyGaps(result)
        ));
    }

    private List<PermissionCheckGrantedByVo> toGrantedBy(PermissionCheckResult result) {
        if (result.getGrantedBy() == null) {
            return null;
        }
        return result.getGrantedBy().stream().map(item -> {
            PermissionCheckGrantedByVo vo = new PermissionCheckGrantedByVo();
            vo.setPermissionId(item.getPermissionId());
            vo.setRoleId(item.getRoleId());
            vo.setResourceId(item.getResourceId());
            vo.setOperationId(item.getOperationId());
            vo.setConditionId(item.getConditionId());
            vo.setCanManage(item.getCanManage());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<PermissionCheckConflictVo> toConflicts(PermissionCheckResult result) {
        if (result.getConflicts() == null) {
            return null;
        }
        return result.getConflicts().stream().map(item -> {
            PermissionCheckConflictVo vo = new PermissionCheckConflictVo();
            vo.setConflictRuleId(item.getConflictRuleId());
            vo.setResourceId(item.getResourceId());
            vo.setFirstOperationId(item.getFirstOperationId());
            vo.setSecondOperationId(item.getSecondOperationId());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<PermissionCheckDependencyGapVo> toDependencyGaps(PermissionCheckResult result) {
        if (result.getDependencyGaps() == null) {
            return null;
        }
        return result.getDependencyGaps().stream().map(item -> {
            PermissionCheckDependencyGapVo vo = new PermissionCheckDependencyGapVo();
            vo.setResourceEntityId(item.getResourceEntityId());
            vo.setOperationPermissionId(item.getOperationPermissionId());
            return vo;
        }).collect(Collectors.toList());
    }
}
