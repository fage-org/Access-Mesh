package cn.ac.fage.accessmesh.access.audit.service.impl;

import cn.ac.fage.accessmesh.access.audit.dto.req.PlatformAuditPageReq;
import cn.ac.fage.accessmesh.access.audit.dto.resp.PlatformAuditResp;
import cn.ac.fage.accessmesh.access.audit.service.PlatformAuditAppService;
import cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService;
import cn.ac.fage.accessmesh.access.auth.security.PlatformAccountGuard;
import cn.ac.fage.accessmesh.access.infrastructure.util.PageUtil;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuditAppServiceImpl implements PlatformAuditAppService {
    private final PlatformAccountGuard guard;
    private final PlatformAuditDomainService audit;

    public PlatformAuditAppServiceImpl(PlatformAccountGuard guard, PlatformAuditDomainService audit) {
        this.guard = guard;
        this.audit = audit;
    }

    @Override
    public PageResp<PlatformAuditResp> page(PlatformAuditPageReq req) {
        guard.requireOperator();
        int number = PageUtil.pageNum(req.pageNum()), size = PageUtil.pageSize(req.pageSize());
        int offset = PageUtil.offset(number, size);
        var items = audit.page(req.targetTenantId(), offset, size).stream().map(PlatformAuditResp::from).toList();
        long total = audit.count(req.targetTenantId());
        return new PageResp<>(items, total, number, size, PageUtil.hasNext(offset, items.size(), total));
    }
}
