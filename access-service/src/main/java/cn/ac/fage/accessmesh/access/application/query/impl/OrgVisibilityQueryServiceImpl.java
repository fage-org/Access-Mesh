package cn.ac.fage.accessmesh.access.application.query.impl;

import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.application.query.OrgVisibilityQueryService;
import cn.ac.fage.accessmesh.access.application.query.mapper.OrgVisibilityQueryMapper;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织可见性查询实现（跨域只读）。
 * <p>
 * 组织树与组织树配置读取经 {@link OrgVisibilityQueryMapper}，操作者主体解析与
 * ADMIN_ORG:VIEW 判定经 {@link TypeResolutionService} / {@link PermQueryEngine}；
 * 默认树可见范围按操作者缓存（ORG_VISIBILITY 目录，L2_ONLY，租户级失效由
 * PermissionChangeAspect 统一执行）。
 * </p>
 */
@Service
public class OrgVisibilityQueryServiceImpl implements OrgVisibilityQueryService {

    private static final Logger log = LoggerFactory.getLogger(OrgVisibilityQueryServiceImpl.class);

    private static final String OPERATION_VIEW = "VIEW";

    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;
    private final OrgVisibilityQueryMapper orgVisibilityQueryMapper;
    private final CacheService cacheService;

    public OrgVisibilityQueryServiceImpl(TypeResolutionService typeResolutionService,
                                         PermQueryEngine engine,
                                         OrgVisibilityQueryMapper orgVisibilityQueryMapper,
                                         CacheService cacheService) {
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.orgVisibilityQueryMapper = orgVisibilityQueryMapper;
        this.cacheService = cacheService;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> filterVisibleOrgIds(Long tenantId, Long operatorId, Collection<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Set.of();
        }
        Long userId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(operatorId));
        if (userId == null) {
            return Set.of();
        }
        // 一次 engine.getDeniedIds 批量查询：先批量解析业务键 → 投影 ID，
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
    @Transactional(readOnly = true)
    public Set<Long> getOperatorVisibleDefaultTreeOrgIds(Long tenantId, Long operatorId) {
        // 缓存不可用时旁路数据库（architecture §7.2：缓存仅加速，查询结果以 DB 与权限引擎为准）
        Set<Long> cached = null;
        try {
            cached = cacheService.get(PermCacheCatalog.ORG_VISIBILITY, tenantId, operatorId);
        } catch (Exception e) {
            log.warn("ORG_VISIBILITY cache get failed, bypassing to DB: tenantId={}, operatorId={}",
                tenantId, operatorId, e);
        }
        if (cached != null) {
            return cached;
        }
        List<Long> rootOrgIds = orgVisibilityQueryMapper.selectDefaultTreeRootOrgIds(tenantId);
        if (rootOrgIds.isEmpty()) {
            return Set.of();
        }
        List<Long> descendantIds = orgVisibilityQueryMapper.selectDescendantOrgIds(tenantId, rootOrgIds.get(0));
        if (descendantIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> visible = filterVisibleOrgIds(tenantId, operatorId, descendantIds);
        try {
            cacheService.put(PermCacheCatalog.ORG_VISIBILITY, tenantId, operatorId, visible);
        } catch (Exception e) {
            log.warn("ORG_VISIBILITY cache put failed, result served from DB: tenantId={}, operatorId={}",
                tenantId, operatorId, e);
        }
        return visible;
    }
}
