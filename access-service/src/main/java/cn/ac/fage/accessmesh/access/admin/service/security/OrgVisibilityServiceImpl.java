package cn.ac.fage.accessmesh.access.admin.service.security;

import cn.ac.fage.accessmesh.access.admin.cache.AdminCacheCatalog;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组织可见性服务实现
 * <p>
 * 通过 {@code PermissionFeignClient.batchCheckAuth(ADMIN_ORG, orgIds, VIEW)} 批量校验，
 * 结果按 {@code (tenantId, operatorId)} 缓存 60 秒（L1 TTL）。
 * 候选集超过 500 时按 500 一批分次调用，防 EXT-7 服务端 N 次循环放大。
 */
@Service
public class OrgVisibilityServiceImpl implements OrgVisibilityService {

    private static final Logger log = LoggerFactory.getLogger(OrgVisibilityServiceImpl.class);

    private static final String SUBJECT_TYPE_CODE = "ADMIN_USER";
    private static final String RESOURCE_TYPE_ADMIN_ORG = "ADMIN_ORG";
    private static final String OPERATION_VIEW = "VIEW";
    private static final int BATCH_SIZE = 500;

    private final PermissionFeignClient permissionFeignClient;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final OrgDomainService orgDomainService;
    private final CacheService cacheService;

    public OrgVisibilityServiceImpl(PermissionFeignClient permissionFeignClient,
                                    OrgTreeConfigDomainService orgTreeConfigDomainService,
                                    OrgDomainService orgDomainService,
                                    CacheService cacheService) {
        this.permissionFeignClient = permissionFeignClient;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.cacheService = cacheService;
    }

    @Override
    public Set<Long> filterVisibleOrgIds(Long tenantId, Long operatorId, Collection<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Set.of();
        }
        List<Long> candidateList = new ArrayList<>(orgIds);
        Set<Long> visible = new LinkedHashSet<>();

        for (int i = 0; i < candidateList.size(); i += BATCH_SIZE) {
            List<Long> batch = candidateList.subList(i, Math.min(i + BATCH_SIZE, candidateList.size()));
            List<BatchAuthCheckReq.AuthCheckItem> items = batch.stream()
                .map(orgId -> new BatchAuthCheckReq.AuthCheckItem(
                    RESOURCE_TYPE_ADMIN_ORG,
                    String.valueOf(orgId),
                    OPERATION_VIEW,
                    null, null, null
                ))
                .toList();

            BatchAuthCheckReq req = new BatchAuthCheckReq(
                SUBJECT_TYPE_CODE,
                String.valueOf(operatorId),
                items,
                null
            );

            try {
                PermResult<BatchAuthCheckResp> result = permissionFeignClient.batchCheckAuth(req);
                if (result != null && result.getCode() == 200 && result.getData() != null && result.getData().items() != null) {
                    for (BatchAuthCheckResp.AuthCheckItemResult item : result.getData().items()) {
                        if (item.allowed()) {
                            try {
                                visible.add(Long.parseLong(item.resourceCode()));
                            } catch (NumberFormatException e) {
                                log.warn("Unexpected resourceCode format in batchCheckAuth result: {}", item.resourceCode());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("batchCheckAuth failed for tenantId={}, operatorId={}, batch offset={}", tenantId, operatorId, i, e);
            }
        }

        return visible;
    }

    @Override
    public Set<Long> getOperatorVisibleDefaultTreeOrgIds(Long tenantId, Long operatorId) {
        // 1. 查缓存
        Set<Long> cached = cacheService.get(AdminCacheCatalog.ORG_VISIBILITY, tenantId, operatorId);
        if (cached != null) {
            return cached;
        }

        // 2. 取默认树后代
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            return Set.of();
        }
        Long defaultRootOrgId = defaultConfigs.get(0).getRootOrgId();
        List<Long> descendantIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, defaultRootOrgId);
        if (descendantIds.isEmpty()) {
            return Set.of();
        }

        // 3. 批量权限过滤
        Set<Long> visible = filterVisibleOrgIds(tenantId, operatorId, descendantIds);

        // 4. 写缓存
        cacheService.put(AdminCacheCatalog.ORG_VISIBILITY, tenantId, operatorId, visible);

        return visible;
    }
}
