package cn.ac.fage.accessmesh.access.sync.service.impl;

import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.PageUtil;
import cn.ac.fage.accessmesh.access.infrastructure.util.StringUtils;
import cn.ac.fage.accessmesh.access.sync.dto.SyncStatusListReq;
import cn.ac.fage.accessmesh.access.sync.dto.SyncStatusResp;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.sync.service.SyncStatusAppService;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncStatusAppServiceImpl implements SyncStatusAppService {
    private final SyncMetadataDomainService metadata;
    private final AdminPermissionValidator permissions;

    public SyncStatusAppServiceImpl(SyncMetadataDomainService metadata, AdminPermissionValidator permissions) {
        this.metadata = metadata;
        this.permissions = permissions;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResp<SyncStatusResp> list(Long tenantId, SyncStatusListReq req) {
        permissions.checkTypeLevel(ResourceTypeCode.DEPENDENCY, OperationCode.VIEW);
        int page = PageUtil.pageNum(req.pageNum());
        int size = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(page, size);
        String source = StringUtils.normalizeFilterParam(req.sourceService());
        long total = metadata.countAppliedScopes(tenantId, source);
        var items = metadata.listAppliedScopes(tenantId, source, size, offset);
        return new PageResp<>(items, total, page, size, PageUtil.hasNext(offset, items.size(), total));
    }
}
