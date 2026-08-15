package cn.ac.fage.accessmesh.access.application.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.security.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.UserOrgWriteAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.aop.PermissionChange;
import cn.ac.fage.accessmesh.access.permission.cache.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserOrgWriteAppServiceImpl implements UserOrgWriteAppService {

    private final UserOrgDomainService userOrgDomainService;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final OrgDomainService orgDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final AuditDomainService auditDomainService;

    public UserOrgWriteAppServiceImpl(UserOrgDomainService userOrgDomainService,
                                      OrgTreeConfigDomainService orgTreeConfigDomainService,
                                      OrgDomainService orgDomainService,
                                      AdminPermissionValidator permissionValidator,
                                      LocalProjectionDomainService localProjectionDomainService,
                                      AuditDomainService auditDomainService) {
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.localProjectionDomainService = localProjectionDomainService;
        this.auditDomainService = auditDomainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "USER_ORG_ASSIGN", targetType = "sys_user_org",
        targetId = "#req.userId()", summary = "'assign user orgs'")
    public void assignUserToOrgs(UserOrgAssignReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<Long> requestedOrgIds = new ArrayList<>(new LinkedHashSet<>(req.orgIds()));
        if (requestedOrgIds.isEmpty()) {
            return;
        }
        Map<Long, SysOrg> orgMap =
            orgDomainService.batchSelectValidByIdsMap(tenantId, new LinkedHashSet<>(requestedOrgIds));
        for (Long orgId : requestedOrgIds) {
            if (!orgMap.containsKey(orgId)) {
                throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                    "org not found, orgId=" + orgId);
            }
        }
        List<String> regularOrgCodes = new ArrayList<>();
        List<String> positionCodes = new ArrayList<>();
        for (Long orgId : requestedOrgIds) {
            if (OrgOperationCodeMapper.isPositionOrg(orgMap.get(orgId).getOrgType())) {
                positionCodes.add(String.valueOf(orgId));
            } else {
                regularOrgCodes.add(String.valueOf(orgId));
            }
        }
        if (!regularOrgCodes.isEmpty()) {
            permissionValidator.checkBatchInstanceLevel(
                AdminResourceType.ORG, regularOrgCodes,
                OrgOperationCodeMapper.resolveForUserOrg(null, AdminOperationCode.UPDATE));
        }
        if (!positionCodes.isEmpty()) {
            permissionValidator.checkBatchInstanceLevel(
                AdminResourceType.ORG, positionCodes,
                OrgOperationCodeMapper.resolveForUserOrg("2", AdminOperationCode.UPDATE));
        }
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        for (SysOrgTreeConfig config : defaultConfigs) {
            if (Boolean.TRUE.equals(config.getSingleAssoc()) && requestedOrgIds.size() > 1) {
                throw new BizException(AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getCode(),
                    AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getMessage());
            }
        }
        Set<Long> existingOrgIds = userOrgDomainService.findByUserId(tenantId, req.userId()).stream()
            .map(SysUserOrg::getOrgId)
            .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        List<SysUserOrg> toInsert = new ArrayList<>();
        for (Long orgId : requestedOrgIds) {
            if (existingOrgIds.contains(orgId)) {
                continue;
            }
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
            userOrgDomainService.insertBatch(toInsert);
            Long abstractUserId = localProjectionDomainService.findAdminUserId(tenantId, req.userId());
            // 九轮评审 P2：批量 BIND（一次批量加载 + 一次批量写），替代循环单条 bindUserOrg N+1；
            // 九轮评审 P1：POSITION 成员 relation 指向所属组织（岗位的 parentId）
            List<LocalProjectionDomainService.UserOrgBindKey> bindKeys = new ArrayList<>();
            for (SysUserOrg assoc : toInsert) {
                SysOrg org = orgMap.get(assoc.getOrgId());
                String roleTypeCode = OrgOperationCodeMapper.isPositionOrg(org.getOrgType()) ? "POSITION" : "ORG";
                bindKeys.add(new LocalProjectionDomainService.UserOrgBindKey(
                    assoc.getUserId(), assoc.getOrgId(), roleTypeCode, org.getParentId()));
            }
            localProjectionDomainService.batchBindUserOrg(tenantId, bindKeys);
            // 八轮评审 P2：变更日志 entityId 用投影主键——批量路径 JDBC batch 无法回填 generated keys，
            // entityId 记 null（九轮评审 P2-8：null 合法，不伪造主键），一次调用批量记录全部 entries
            Long[] affected = abstractUserId == null ? new Long[]{} : new Long[]{abstractUserId};
            auditDomainService.recordChangeLog(
                new AuditDomainService.ChangeLogContext(
                    tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
                toInsert.stream()
                    .map(assoc -> new AuditDomainService.ChangeLogEntry(
                        "user_role", null, "BIND", null, null, null, affected, new Long[0]))
                    .collect(Collectors.toList()));
            if (abstractUserId != null) {
                PermissionChangeContext.markUsers(tenantId, Set.of(abstractUserId));
            }
        }
        if (req.primaryOrgId() != null
            && toInsert.stream().noneMatch(a -> a.getOrgId().equals(req.primaryOrgId()))) {
            List<Long> defaultOrgIds = resolveDefaultTreeOrgIds(tenantId, defaultConfigs);
            if (defaultOrgIds.contains(req.primaryOrgId())) {
                userOrgDomainService.setPrimaryOrgInScope(
                    tenantId, req.userId(), req.primaryOrgId(), defaultOrgIds);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "USER_ORG_REMOVE", targetType = "sys_user_org",
        targetId = "#userId", summary = "'remove user org'")
    public void removeUserFromOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysOrg org = orgDomainService.selectValidById(tenantId, orgId);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                "user-org unbind: org not found, orgId=" + orgId);
        }
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        List<Long> defaultTreeOrgIds = resolveDefaultTreeOrgIds(tenantId, defaultConfigs);
        boolean isDefaultTreeOrg = defaultTreeOrgIds.contains(orgId);
        if (isDefaultTreeOrg) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER, String.valueOf(userId), AdminOperationCode.UPDATE);
            List<SysUserOrg> userDefaultOrgs = userOrgDomainService.findByUserId(tenantId, userId).stream()
                .filter(uo -> defaultTreeOrgIds.contains(uo.getOrgId()) && !uo.getOrgId().equals(orgId))
                .collect(Collectors.toList());
            if (userDefaultOrgs.isEmpty()) {
                throw new BizException(AdminErrorCode.USER_LOSE_DEFAULT_TREE_HOME.getCode(),
                    AdminErrorCode.USER_LOSE_DEFAULT_TREE_HOME.getMessage());
            }
        } else {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.ORG, String.valueOf(orgId),
                OrgOperationCodeMapper.resolveForUserOrg(org.getOrgType(), AdminOperationCode.UPDATE));
        }
        userOrgDomainService.deleteByUserIdAndOrgId(tenantId, userId, orgId);
        String roleTypeCode = OrgOperationCodeMapper.isPositionOrg(org.getOrgType()) ? "POSITION" : "ORG";
        Long abstractUserId = localProjectionDomainService.findAdminUserId(tenantId, userId);
        // 八轮评审 P2：entityId 用 user_role.id（投影主键，软删前取得），不再混用 sys_user.id；
        // 九轮评审 P1：POSITION 成员 relation 指向所属组织（岗位的 parentId）
        Long userRoleId = localProjectionDomainService.unbindUserOrg(
            tenantId, userId, orgId, roleTypeCode, org.getParentId());
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "user_role", userRoleId, "UNBIND", null, null, null,
                abstractUserId == null ? new Long[]{} : new Long[]{abstractUserId}, new Long[0])));
        if (abstractUserId != null) {
            PermissionChangeContext.markUsers(tenantId, Set.of(abstractUserId));
        }
    }

    private List<Long> resolveDefaultTreeOrgIds(Long tenantId, List<SysOrgTreeConfig> defaultConfigs) {
        if (defaultConfigs.isEmpty()) {
            return List.of();
        }
        Long rootOrgId = defaultConfigs.get(0).getRootOrgId();
        return orgDomainService.getDescendantIdsIncludingSelf(tenantId, rootOrgId);
    }

    private static Long operatorId() {
        Long id = AccessRequestContext.getOperatorId();
        return id != null ? id : StpUtil.getLoginIdAsLong();
    }
}
