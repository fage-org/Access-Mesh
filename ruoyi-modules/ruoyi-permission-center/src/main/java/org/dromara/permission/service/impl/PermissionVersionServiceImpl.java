package org.dromara.permission.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.mapper.PcPermissionVersionMapper;
import org.dromara.permission.service.PermissionVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 权限版本服务实现
 *
 * @author RuoYi-Cloud-Plus
 */
@Service
@RequiredArgsConstructor
public class PermissionVersionServiceImpl implements PermissionVersionService {

    private final PcPermissionVersionMapper mapper;

    @Override
    public PcPermissionVersion queryCurrentVersion(Long tenantId) {
        PcPermissionVersion current = mapper.selectLatestByTenant(tenantId);
        if (current != null) {
            return current;
        }
        PcPermissionVersion initial = new PcPermissionVersion();
        initial.setTenantId(tenantId);
        initial.setVersionNo(0L);
        initial.setUpdatedAt(LocalDateTime.now());
        initial.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return initial;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PcPermissionVersion bumpVersion(Long tenantId, String triggerEntityType, Long triggerEntityId, String remark) {
        mapper.lockTenantVersion(tenantId);
        PcPermissionVersion current = queryCurrentVersion(tenantId);
        LocalDateTime now = LocalDateTime.now();

        PcPermissionVersion next = new PcPermissionVersion();
        next.setTenantId(tenantId);
        next.setVersionNo(current.getVersionNo() + 1);
        next.setTriggerEntityType(triggerEntityType);
        next.setTriggerEntityId(triggerEntityId);
        next.setRemark(remark);
        next.setCreatedAt(now);
        next.setUpdatedAt(now);
        next.setDeleteFlag(PermissionConstants.NOT_DELETED);
        mapper.insert(next);
        return next;
    }

    @Override
    public String buildVersionToken(Long tenantId, Long versionNo) {
        long resolvedVersion = versionNo == null ? 0L : versionNo;
        return tenantId + "-v" + resolvedVersion;
    }
}
