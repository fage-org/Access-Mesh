package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.cache.AdminCacheCatalog;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.security.OrgVisibilityService;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组织可见性：本地 PermQueryEngine 批量校验，结果按操作者缓存。
 */
@Service
@Primary
public class OrgVisibilityServiceImpl implements OrgVisibilityService {

    private static final String OPERATION_VIEW = "VIEW";

    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final OrgDomainService orgDomainService;
    private final CacheService cacheService;

    public OrgVisibilityServiceImpl(TypeResolutionService typeResolutionService,
                                    PermQueryEngine engine,
                                    OrgTreeConfigDomainService orgTreeConfigDomainService,
                                    OrgDomainService orgDomainService,
                                    CacheService cacheService) {
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.cacheService = cacheService;
    }

    @Override
    public Set<Long> filterVisibleOrgIds(Long tenantId, Long operatorId, Collection<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Set.of();
        }
        Long userId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(operatorId));
        if (userId == null) {
            return Set.of();
        }
        // T-ACCESS-005 评审 P2：一次 engine.getDeniedIds 批量查询，替代 BATCH_SIZE 切片内的逐组织单查
        List<Long> candidateList = new ArrayList<>(orgIds);
        Set<Long> denied = engine.getDeniedIds(
            tenantId, userId, AdminResourceType.ORG, new LinkedHashSet<>(candidateList), OPERATION_VIEW);
        candidateList.removeAll(denied);
        return new LinkedHashSet<>(candidateList);
    }

    @Override
    public Set<Long> getOperatorVisibleDefaultTreeOrgIds(Long tenantId, Long operatorId) {
        Set<Long> cached = cacheService.get(AdminCacheCatalog.ORG_VISIBILITY, tenantId, operatorId);
        if (cached != null) {
            return cached;
        }
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            return Set.of();
        }
        Long defaultRootOrgId = defaultConfigs.get(0).getRootOrgId();
        List<Long> descendantIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, defaultRootOrgId);
        if (descendantIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> visible = filterVisibleOrgIds(tenantId, operatorId, descendantIds);
        cacheService.put(AdminCacheCatalog.ORG_VISIBILITY, tenantId, operatorId, visible);
        return visible;
    }
}
