package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper.DescendantResult;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef.SYS_ORG;

@Service
public class OrgDomainServiceImpl implements OrgDomainService {

    private final SysOrgMapper orgMapper;

    public OrgDomainServiceImpl(SysOrgMapper orgMapper) {
        this.orgMapper = orgMapper;
    }

    @Override
    public List<Long> getDescendantIds(Long tenantId, Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        List<Long> ids = orgMapper.selectDescendantIds(tenantId, orgId);
        return ids != null ? ids : List.of();
    }

    @Override
    public List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        List<Long> ids = orgMapper.selectDescendantIdsIncludingSelf(tenantId, orgId);
        return ids != null ? ids : List.of();
    }

    @Override
    public Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Initialize result with empty lists for each input ID
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long id : orgIds) {
            result.put(id, new ArrayList<>());
        }

        // Performance fix: Use single batch CTE query instead of N+1 queries
        List<DescendantResult> descendants = orgMapper.selectBatchDescendantIds(tenantId, orgIds);
        for (DescendantResult dr : descendants) {
            Long rootId = dr.getOrgId();
            Long descId = dr.getDescendantId();
            if (rootId != null && descId != null) {
                result.computeIfAbsent(rootId, k -> new ArrayList<>()).add(descId);
            }
        }

        return result;
    }

    @Override
    public List<Long> getAncestorIds(Long tenantId, Long orgId) {
        // Performance fix: Delegate to batchGetAncestorIds to avoid N+1 queries
        Map<Long, List<Long>> ancestorMap = batchGetAncestorIds(tenantId, Set.of(orgId));
        return ancestorMap.getOrDefault(orgId, List.of());
    }

    @Override
    public Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, SysOrg> entityMap = new HashMap<>();
        Set<Long> toLoad = new HashSet<>(orgIds);

        while (!toLoad.isEmpty()) {
            List<SysOrg> loaded = orgMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(SYS_ORG.TENANT_ID.eq(tenantId))
                    .and(SYS_ORG.ID.in(toLoad))
                    .and(SYS_ORG.DELETE_FLAG.eq(0))
            );
            toLoad.clear();
            for (SysOrg org : loaded) {
                entityMap.put(org.getId(), org);
                if (org.getParentId() != null && org.getParentId() != 0L
                    && !entityMap.containsKey(org.getParentId())) {
                    toLoad.add(org.getParentId());
                }
            }
        }

        Map<Long, List<Long>> result = new HashMap<>();
        for (Long orgId : orgIds) {
            List<Long> ancestors = new ArrayList<>();
            Long current = orgId;
            while (current != null) {
                SysOrg org = entityMap.get(current);
                if (org == null) {
                    break;
                }
                if (org.getParentId() != null && org.getParentId() != 0L) {
                    ancestors.add(org.getParentId());
                    current = org.getParentId();
                } else {
                    break;
                }
            }
            result.put(orgId, ancestors);
        }
        return result;
    }

    @Override
    public SysOrg selectValidById(Long tenantId, Long orgId) {
        if (orgId == null) {
            return null;
        }
        return orgMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_ORG.ID.eq(orgId))
                .and(SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SYS_ORG.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public List<SysOrg> selectValidByIds(Long tenantId, Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return List.of();
        }
        return orgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SYS_ORG.ID.in(orgIds))
                .and(SYS_ORG.DELETE_FLAG.eq(0))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteBatch(Long tenantId, List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return;
        }
        orgMapper.softDeleteBatch(tenantId, orgIds, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithChildren(Long tenantId, Long orgId) {
        SysOrg org = selectValidById(tenantId, orgId);
        if (org == null) return;

        List<Long> allIds = orgMapper.selectDescendantIdsIncludingSelf(tenantId, orgId);
        orgMapper.softDeleteBatch(tenantId, allIds, LocalDateTime.now());
    }

    @Override
    public boolean hasChildren(Long tenantId, Long orgId) {
        long count = orgMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SYS_ORG.PARENT_ID.eq(orgId))
                .and(SYS_ORG.DELETE_FLAG.eq(0))
        );
        return count > 0;
    }

    @Override
    public SysOrg findByCode(Long tenantId, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return orgMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SYS_ORG.CODE.eq(code))
                .and(SYS_ORG.DELETE_FLAG.eq(0))
        );
    }
}