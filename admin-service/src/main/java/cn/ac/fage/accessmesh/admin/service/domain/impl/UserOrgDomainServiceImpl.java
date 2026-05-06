package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;

@Service
public class UserOrgDomainServiceImpl implements UserOrgDomainService {

    private final SysUserOrgMapper userOrgMapper;
    private final OrgDomainService orgDomainService;

    public UserOrgDomainServiceImpl(SysUserOrgMapper userOrgMapper, OrgDomainService orgDomainService) {
        this.userOrgMapper = userOrgMapper;
        this.orgDomainService = orgDomainService;
    }

    @Override
    public List<SysUserOrg> findByUserId(Long tenantId, Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUserId(Long tenantId, Long userId) {
        if (userId == null) {
            return;
        }
        userOrgMapper.deleteByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUserIdAndOrgId(Long tenantId, Long userId, Long orgId) {
        if (userId == null || orgId == null) {
            return;
        }
        userOrgMapper.deleteByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.ORG_ID.eq(orgId))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertBatch(List<SysUserOrg> userOrgs) {
        if (userOrgs == null || userOrgs.isEmpty()) {
            return;
        }
        userOrgMapper.insertBatch(userOrgs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrimaryOrg(Long tenantId, Long userId, Long orgId) {
        if (userId == null || orgId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        // 将目标组织设为主组织
        SysUserOrg updatePrimary = new SysUserOrg();
        updatePrimary.setIsPrimary(true);
        updatePrimary.setUpdatedAt(now);
        userOrgMapper.updateByQuery(updatePrimary, QueryWrapper.create()
            .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.ORG_ID.eq(orgId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        // 将其他组织设为非主组织
        SysUserOrg updateNonPrimary = new SysUserOrg();
        updateNonPrimary.setIsPrimary(false);
        updateNonPrimary.setUpdatedAt(now);
        userOrgMapper.updateByQuery(updateNonPrimary, QueryWrapper.create()
            .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.ORG_ID.ne(orgId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public List<UserPageItemResp.OrgBrief> getUserOrgBriefs(Long tenantId, Long userId) {
        List<SysUserOrg> userOrgs = findByUserId(tenantId, userId);
        if (userOrgs.isEmpty()) {
            return List.of();
        }

        // 批量查询组织信息（避免N+1问题）
        Set<Long> orgIds = userOrgs.stream().map(SysUserOrg::getOrgId).collect(Collectors.toSet());
        Map<Long, SysOrg> orgMap = orgDomainService.batchSelectValidByIdsMap(tenantId, orgIds);

        return userOrgs.stream().map(uo -> {
            SysOrg org = orgMap.get(uo.getOrgId());
            String orgName = org != null ? org.getName() : null;
            String orgType = org != null ? org.getOrgType() : null;
            return new UserPageItemResp.OrgBrief(uo.getOrgId(), orgName, orgType, Boolean.TRUE.equals(uo.getIsPrimary()));
        }).collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<SysUserOrg>> batchFindByUserIds(Long tenantId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<SysUserOrg> allUserOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.in(userIds))
                .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        Map<Long, List<SysUserOrg>> result = new HashMap<>();
        for (Long userId : userIds) {
            result.put(userId, new java.util.ArrayList<>());
        }
        for (SysUserOrg uo : allUserOrgs) {
            result.computeIfAbsent(uo.getUserId(), k -> new java.util.ArrayList<>()).add(uo);
        }
        return result;
    }
}