package org.dromara.permission.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.permission.condition.PermissionConditionContextSupport;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm")
public class PermissionCheckController {

    private final PermissionService permissionService;

    @PostMapping("/check")
    public R<PermissionCheckVo> check(@Validated @RequestBody PermissionCheckReq req, HttpServletRequest servletRequest) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setTenantId(req.getTenantId());
        request.setAbstractUserId(req.getUserId());
        request.setResourceEntityId(req.getResourceEntityId());
        request.setOperationPermissionId(req.getOperationPermissionId());
        request.setBizDomainId(req.getBizDomainId());
        request.setInheritMode(req.getInheritMode());
        request.setCheckDependency(req.getCheckDependency());
        request.setContext(PermissionConditionContextSupport.extractBusinessContext(req.getContext()));
        request.setTrustedContext(buildTrustedContext(servletRequest));
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

    private Map<String, Object> buildTrustedContext(HttpServletRequest servletRequest) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (servletRequest != null) {
            context.put("request", buildRequestContext(servletRequest));
            context.put("network", buildNetworkContext(servletRequest));
        }
        return context;
    }

    private Map<String, Object> buildRequestContext(HttpServletRequest servletRequest) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> requestContext = new LinkedHashMap<>();
        requestContext.put("currentDateTime", now);
        requestContext.put("currentDate", LocalDate.from(now));
        requestContext.put("httpMethod", servletRequest.getMethod());
        requestContext.put("method", servletRequest.getMethod());
        requestContext.put("httpPath", servletRequest.getRequestURI());
        requestContext.put("path", servletRequest.getRequestURI());
        String requestId = resolveRequestId(servletRequest);
        if (requestId != null && !requestId.isBlank()) {
            requestContext.put("requestId", requestId);
        }
        return requestContext;
    }

    private Map<String, Object> buildNetworkContext(HttpServletRequest servletRequest) {
        Map<String, Object> networkContext = new LinkedHashMap<>();
        networkContext.put("clientIp", ServletUtils.getClientIP(servletRequest));
        networkContext.put("remoteIp", servletRequest.getRemoteAddr());
        return networkContext;
    }

    private String resolveRequestId(HttpServletRequest servletRequest) {
        String requestId = servletRequest.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = servletRequest.getHeader("requestId");
        }
        if (requestId == null || requestId.isBlank()) {
            Object attribute = servletRequest.getAttribute("requestId");
            if (attribute instanceof String text && !text.isBlank()) {
                requestId = text;
            }
        }
        return requestId;
    }
}
