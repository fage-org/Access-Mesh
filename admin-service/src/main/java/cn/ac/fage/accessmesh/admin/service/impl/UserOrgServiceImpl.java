package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysOrgTreeConfigTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.dev33.satoken.stp.StpUtil;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class UserOrgServiceImpl implements UserOrgService {

    private final SysUserOrgMapper userOrgMapper;
    private final SysOrgMapper orgMapper;
    private final SysOrgTreeConfigMapper orgTreeConfigMapper;
    private final AdminPermissionValidator permissionValidator;

    public UserOrgServiceImpl(SysUserOrgMapper userOrgMapper, SysOrgMapper orgMapper,
                              SysOrgTreeConfigMapper orgTreeConfigMapper,
                              AdminPermissionValidator permissionValidator) {
        this.userOrgMapper = userOrgMapper;
        this.orgMapper = orgMapper;
        this.orgTreeConfigMapper = orgTreeConfigMapper;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional
    public void assignUserToOrgs(UserOrgAssignReq req) {
        // FIX #1: Add permission validation - user org assignment is an UPDATE operation on USER resource
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = StpUtil.getLoginIdAsLong();

        // Self-assignment exemption: user can assign own orgs without permission check
        if (!req.userId().equals(operatorId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(req.userId()),
                AdminOperationCode.UPDATE
            );
        }

        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.TENANT_ID.eq(tenantId))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.DELETE_FLAG.eq(0))
                .and(SysOrgTreeConfigTableDef.SYS_ORG_TREE_CONFIG.IS_DEFAULT.eq(true))
        );
        for (SysOrgTreeConfig config : defaultConfigs) {
            if (Boolean.TRUE.equals(config.getSingleAssoc()) && req.orgIds().size() > 1) {
                throw new BizException(AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getCode(),
                    AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getMessage());
            }
        }

        userOrgMapper.deleteByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(req.userId()))
        );

        LocalDateTime now = LocalDateTime.now();
        // Performance fix: collect entities for batch insert
        List<SysUserOrg> toInsert = new ArrayList<>();
        for (Long orgId : req.orgIds()) {
            SysUserOrg assoc = new SysUserOrg();
            assoc.setTenantId(tenantId);
            assoc.setUserId(req.userId());
            assoc.setOrgId(orgId);
            assoc.setIsPrimary(orgId.equals(req.primaryOrgId()));
            assoc.setCreatedAt(now);
            assoc.setUpdatedAt(now);
            assoc.setDeleteFlag(0L);
            toInsert.add(assoc);
        }
        if (!toInsert.isEmpty()) {
            userOrgMapper.insertBatch(toInsert);
        }
    }

    @Override
    @Transactional
    public void removeUserFromOrg(Long userId, Long orgId) {
        // FIX #1: Add permission validation
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = StpUtil.getLoginIdAsLong();

        // Self-removal exemption: user can remove own org association
        if (!userId.equals(operatorId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(userId),
                AdminOperationCode.UPDATE
            );
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
    public void setPrimaryOrg(Long userId, Long orgId) {
        // FIX #1: Add permission validation
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = StpUtil.getLoginIdAsLong();

        // Self-modification exemption: user can set own primary org
        if (!userId.equals(operatorId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(userId),
                AdminOperationCode.UPDATE
            );
        }

        LocalDateTime now = LocalDateTime.now();

        // 批量更新：将该用户在目标组织的主组织状态设为 true
        SysUserOrg updatePrimary = new SysUserOrg();
        updatePrimary.setIsPrimary(true);
        updatePrimary.setUpdatedAt(now);
        userOrgMapper.updateByQuery(updatePrimary, QueryWrapper.create()
            .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.ORG_ID.eq(orgId))
            .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        // 批量更新：将该用户在其他组织的主组织状态设为 false
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
    public List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId) {
        // FIX: Add tenantId filter for security
        Long tenantId = TenantContextHolder.getTenantId();

        List<SysUserOrg> userOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        if (userOrgs.isEmpty()) {
            return List.of();
        }

        // 批量查询组织信息（避免N+1问题）
        Set<Long> orgIds = userOrgs.stream().map(SysUserOrg::getOrgId).collect(Collectors.toSet());
        List<SysOrg> orgs = orgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysOrgTableDef.SYS_ORG.TENANT_ID.eq(tenantId))
                .and(SysOrgTableDef.SYS_ORG.ID.in(orgIds))
                .and(SysOrgTableDef.SYS_ORG.DELETE_FLAG.eq(0))
        );
        Map<Long, SysOrg> orgMap = orgs.stream()
            .collect(Collectors.toMap(SysOrg::getId, o -> o));

        return userOrgs.stream().map(uo -> {
            SysOrg org = orgMap.get(uo.getOrgId());
            String orgName = org != null ? org.getName() : null;
            String orgType = org != null ? org.getOrgType() : null;
            return new UserPageItemResp.OrgBrief(uo.getOrgId(), orgName, orgType, Boolean.TRUE.equals(uo.getIsPrimary()));
        }).collect(Collectors.toList());
    }
}
