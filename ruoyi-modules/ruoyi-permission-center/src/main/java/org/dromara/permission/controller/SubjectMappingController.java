package org.dromara.permission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.authcenter.api.model.PermissionVersionInfo;
import org.dromara.authcenter.api.request.PermissionVersionQueryRequest;
import org.dromara.common.core.domain.R;
import org.dromara.permission.model.subject.SubjectMappingRequest;
import org.dromara.permission.model.subject.SubjectMappingResponse;
import org.dromara.permission.service.SubjectMappingService;
import org.dromara.permission.service.PermissionVersionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主体映射接口
 *
 * 供 identity-service 调用，用于主体映射和版本查询
 *
 * @author RuoYi-Cloud-Plus
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/perm/subject")
public class SubjectMappingController {

    private final SubjectMappingService subjectMappingService;
    private final PermissionVersionService permissionVersionService;

    /**
     * 查找或创建抽象用户
     */
    @PostMapping("/mapping")
    public R<SubjectMappingResponse> findOrCreateMapping(@Valid @RequestBody SubjectMappingRequest request) {
        Long tenantId = Long.valueOf(request.getTenantId());
        Integer userType = subjectMappingService.getUserTypeValue(request.getUserTypeCode());

        var user = subjectMappingService.findOrCreate(
            tenantId,
            userType,
            request.getExternalId(),
            request.getName()
        );

        var version = permissionVersionService.queryCurrentVersion(tenantId);

        SubjectMappingResponse response = new SubjectMappingResponse();
        response.setAbstractUserId(user.getId());
        response.setPermissionVersion(tenantId + "-v" + version.getVersionNo());
        response.setVersionUpdatedAt(version.getUpdatedAt().atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli());

        return R.ok(response);
    }

    /**
     * 查询权限版本
     */
    @PostMapping("/version")
    public R<PermissionVersionInfo> queryVersion(@Valid @RequestBody PermissionVersionQueryRequest request) {
        Long tenantId = Long.valueOf(request.getTenantId());
        var version = permissionVersionService.queryCurrentVersion(tenantId);

        PermissionVersionInfo info = new PermissionVersionInfo();
        info.setTenantId(request.getTenantId());
        info.setSubjectKey(request.getSubjectType() + ":" + request.getSubjectId());
        info.setPermissionVersion(tenantId + "-v" + version.getVersionNo());
        info.setUpdatedAtEpochMilli(version.getUpdatedAt().atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli());

        return R.ok(info);
    }
}
