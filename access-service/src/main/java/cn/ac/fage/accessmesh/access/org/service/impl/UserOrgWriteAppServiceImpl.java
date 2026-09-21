package cn.ac.fage.accessmesh.access.org.service.impl;

import cn.ac.fage.accessmesh.access.org.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.org.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.engine.constant.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.UserOrgWriteAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.projection.PermConstants;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.projection.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
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
    private final TreeWriteLockSupport treeWriteLockSupport;

    public UserOrgWriteAppServiceImpl(UserOrgDomainService userOrgDomainService,
                                      OrgTreeConfigDomainService orgTreeConfigDomainService,
                                      OrgDomainService orgDomainService,
                                      AdminPermissionValidator permissionValidator,
                                      LocalProjectionDomainService localProjectionDomainService,
                                      AuditDomainService auditDomainService,
                                      TreeWriteLockSupport treeWriteLockSupport) {
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.localProjectionDomainService = localProjectionDomainService;
        this.auditDomainService = auditDomainService;
        this.treeWriteLockSupport = treeWriteLockSupport;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "USER_ORG_ASSIGN", targetType = "sys_user_org",
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
                throw new BizException(AccessErrorCode.ORG_NOT_FOUND.getCode(),
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
                ResourceTypeCode.ORG, regularOrgCodes,
                OrgOperationCodeMapper.resolveForUserOrg(null, OperationCode.UPDATE));
        }
        if (!positionCodes.isEmpty()) {
            permissionValidator.checkBatchInstanceLevel(
                ResourceTypeCode.ORG, positionCodes,
                OrgOperationCodeMapper.resolveForUserOrg("2", OperationCode.UPDATE));
        }
        // SYS_ORG 树锁（claude 外评 P2）：归属写与组织结构写/树配置守卫串行——否则并发
        // deleteOrg/setDefault 的守卫读与本次挂载交错，切默认后新归属落在旧树、或
        // 与并发移除双放行致用户默认树归属归 0；锁覆盖守卫读（findDefaultConfigs/
        // existingOrgIds）与全部写
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        for (SysOrgTreeConfig config : defaultConfigs) {
            if (Boolean.TRUE.equals(config.getSingleAssoc()) && requestedOrgIds.size() > 1) {
                throw new BizException(AccessErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getCode(),
                    AccessErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getMessage());
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
            // 批量 BIND（一次批量加载 + 一次批量写），替代循环单条 bindUserOrg N+1；
            // POSITION 成员 relation 指向所属组织（岗位的 parentId）
            List<LocalProjectionDomainService.UserOrgBindKey> bindKeys = new ArrayList<>();
            for (SysUserOrg assoc : toInsert) {
                SysOrg org = orgMap.get(assoc.getOrgId());
                String roleTypeCode = OrgOperationCodeMapper.isPositionOrg(org.getOrgType()) ? "POSITION" : "ORG";
                bindKeys.add(new LocalProjectionDomainService.UserOrgBindKey(
                    assoc.getUserId(), assoc.getOrgId(), roleTypeCode, org.getParentId()));
            }
            // 批量 BIND 返回 key → user_role.id（插入后批量回查），
            // 变更日志 entityId 保持八轮决策（投影主键），不再写 null
            Map<LocalProjectionDomainService.UserOrgBindKey, Long> roleIdByKey =
                localProjectionDomainService.batchBindUserOrg(tenantId, bindKeys);
            Long[] affected = abstractUserId == null ? new Long[]{} : new Long[]{abstractUserId};
            auditDomainService.recordChangeLog(
                new AuditDomainService.ChangeLogContext(
                    tenantId, operatorId(), OperatorContext.getRequestId(), PermConstants.MaintainSource.MANUAL, "local-projection"),
                bindKeys.stream()
                    .map(key -> new AuditDomainService.ChangeLogEntry(
                        "user_role", roleIdByKey.get(key), "BIND", null, null, null, affected, new Long[0]))
                    .collect(Collectors.toList()));
            if (abstractUserId != null) {
                PermissionChangeContext.markUsers(tenantId, Set.of(abstractUserId));
            }
        }
        if (req.primaryOrgId() != null
            && toInsert.stream().noneMatch(a -> a.getOrgId().equals(req.primaryOrgId()))) {
            List<Long> defaultOrgIds = orgTreeConfigDomainService.resolveDefaultTreeOrgIds(tenantId);
            if (defaultOrgIds.contains(req.primaryOrgId())) {
                userOrgDomainService.setPrimaryOrgInScope(
                    tenantId, req.userId(), req.primaryOrgId(), defaultOrgIds);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "USER_ORG_REMOVE", targetType = "sys_user_org",
        targetId = "#userId", summary = "'remove user org'")
    public void removeUserFromOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysOrg org = orgDomainService.selectValidById(tenantId, orgId);
        if (org == null) {
            throw new BizException(AccessErrorCode.ORG_NOT_FOUND.getCode(),
                "user-org unbind: org not found, orgId=" + orgId);
        }
        // SYS_ORG 树锁（claude 外评 P2）：守卫读（默认树范围+用户归属）与删除全部入锁——
        // 否则与并发 deleteOrg 的守卫读双放行（各自见对方未提交前的「仍有其他归属」），
        // 用户默认树归属被并发拆空（F001 同类后果）；锁先于守卫首读
        // （resolveDefaultTreeOrgIds），org 存在性解析非守卫输入留在锁前
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
        List<Long> defaultTreeOrgIds = orgTreeConfigDomainService.resolveDefaultTreeOrgIds(tenantId);
        boolean isDefaultTreeOrg = defaultTreeOrgIds.contains(orgId);
        if (isDefaultTreeOrg) {
            permissionValidator.checkInstanceLevel(
                ResourceTypeCode.USER, String.valueOf(userId), OperationCode.UPDATE);
            // 最后归属保护换绑共享守卫（T-ORG-002）：与组织删除级联、树配置切根同一判定语义，
            // 避免 AppService 内联过滤与共享守卫语义漂移。成员场景与原内联实现等价；
            // 非成员请求（用户本不属于该 org）由原实现的误拒 11013 修正为幂等放行
            // （候选集=该 org 现成员，非成员不在候选集，删除 0 行）
            Set<Long> retainedOrgIds = new java.util.HashSet<>(defaultTreeOrgIds);
            retainedOrgIds.remove(orgId);
            Set<Long> losingUserIds = orgTreeConfigDomainService.findUsersLosingDefaultHome(
                tenantId, new java.util.HashSet<>(defaultTreeOrgIds), retainedOrgIds);
            if (losingUserIds.contains(userId)) {
                throw new BizException(AccessErrorCode.USER_LOSE_DEFAULT_TREE_HOME.getCode(),
                    AccessErrorCode.USER_LOSE_DEFAULT_TREE_HOME.getMessage());
            }
        } else {
            permissionValidator.checkInstanceLevel(
                ResourceTypeCode.ORG, String.valueOf(orgId),
                OrgOperationCodeMapper.resolveForUserOrg(org.getOrgType(), OperationCode.UPDATE));
        }
        userOrgDomainService.deleteByUserIdAndOrgId(tenantId, userId, orgId);
        String roleTypeCode = OrgOperationCodeMapper.isPositionOrg(org.getOrgType()) ? "POSITION" : "ORG";
        Long abstractUserId = localProjectionDomainService.findAdminUserId(tenantId, userId);
        // entityId 用 user_role.id（投影主键，软删前取得），不再混用 sys_user.id；
        // POSITION 成员 relation 指向所属组织（岗位的 parentId）
        Long userRoleId = localProjectionDomainService.unbindUserOrg(
            tenantId, userId, orgId, roleTypeCode, org.getParentId());
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId(), OperatorContext.getRequestId(), PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "user_role", userRoleId, "UNBIND", null, null, null,
                abstractUserId == null ? new Long[]{} : new Long[]{abstractUserId}, new Long[0])));
        if (abstractUserId != null) {
            PermissionChangeContext.markUsers(tenantId, Set.of(abstractUserId));
        }
    }

    private static Long operatorId() {
        Long id = AccessRequestContext.getOperatorId();
        return id != null ? id : StpUtil.getLoginIdAsLong();
    }
}
