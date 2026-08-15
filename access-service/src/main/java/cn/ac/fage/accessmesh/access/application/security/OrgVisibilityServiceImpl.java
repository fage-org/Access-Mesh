package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.security.OrgVisibilityService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        // 八轮评审 P2：一次 engine.getDeniedIds 批量查询，替代切片内逐组织单查。
        // 九轮评审 P1 修复：getDeniedIds 按 resource_entity.id 查询，先批量解析业务键 → 投影 ID，
        // denied 结果映射回组织 ID；未解析（无投影）的组织视为不可见（与单条 query deny 语义一致）
        List<Long> candidateList = new ArrayList<>(orgIds);
        List<ResourceResolveRequest> requests = candidateList.stream()
            .map(orgId -> new ResourceResolveRequest(AdminResourceType.ORG, String.valueOf(orgId), null, null))
            .toList();
        Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(tenantId, requests);
        Map<Long, Long> entityIdByOrgId = new LinkedHashMap<>();
        for (Long orgId : candidateList) {
            Long entityId = resolved.get(
                new ResourceResolveKey(AdminResourceType.ORG, String.valueOf(orgId), null, null));
            if (entityId != null) {
                entityIdByOrgId.put(orgId, entityId);
            }
        }
        if (entityIdByOrgId.isEmpty()) {
            return Set.of();
        }
        Set<Long> deniedEntityIds = engine.getDeniedIds(
            tenantId, userId, AdminResourceType.ORG,
            new LinkedHashSet<>(entityIdByOrgId.values()), OPERATION_VIEW);
        Set<Long> visible = new LinkedHashSet<>();
        for (Map.Entry<Long, Long> entry : entityIdByOrgId.entrySet()) {
            if (!deniedEntityIds.contains(entry.getValue())) {
                visible.add(entry.getKey());
            }
        }
        return visible;
    }

    @Override
    public Set<Long> getOperatorVisibleDefaultTreeOrgIds(Long tenantId, Long operatorId) {
        Set<Long> cached = cacheService.get(PermCacheCatalog.ORG_VISIBILITY, tenantId, operatorId);
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
        cacheService.put(PermCacheCatalog.ORG_VISIBILITY, tenantId, operatorId, visible);
        return visible;
    }
}
