package cn.ac.fage.accessmesh.access.application.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.security.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.OrgWriteAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.aop.PermissionChange;
import cn.ac.fage.accessmesh.access.permission.cache.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class OrgWriteAppServiceImpl implements OrgWriteAppService {

    private final OrgDomainService orgDomainService;
    private final UserOrgDomainService userOrgDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final AuditDomainService auditDomainService;
    private final ObjectMapper objectMapper;

    public OrgWriteAppServiceImpl(OrgDomainService orgDomainService,
                                  UserOrgDomainService userOrgDomainService,
                                  AdminPermissionValidator permissionValidator,
                                  LocalProjectionDomainService localProjectionDomainService,
                                  AuditDomainService auditDomainService,
                                  ObjectMapper objectMapper) {
        this.orgDomainService = orgDomainService;
        this.userOrgDomainService = userOrgDomainService;
        this.permissionValidator = permissionValidator;
        this.localProjectionDomainService = localProjectionDomainService;
        this.auditDomainService = auditDomainService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "ORG_CREATE", targetType = "sys_org",
        targetId = "#result", summary = "'create org ' + #req.code()")
    public Long createOrg(OrgCreateReq req) {
        String orgType = req.orgType() != null ? String.valueOf(req.orgType()) : null;
        permissionValidator.checkTypeLevel(
            AdminResourceType.ORG, OrgOperationCodeMapper.resolve(orgType, AdminOperationCode.CREATE));
        Long tenantId = TenantContextHolder.getTenantId();
        if (orgDomainService.findByCode(tenantId, req.code()) != null) {
            throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(),
                AdminErrorCode.ORG_CODE_EXISTS.getMessage());
        }
        int level = 1;
        if (req.parentOrgId() != null) {
            SysOrg parent = orgDomainService.selectValidById(tenantId, req.parentOrgId());
            if (parent != null) {
                level = parent.getLevel() != null ? parent.getLevel() + 1 : 1;
            }
        }
        if (level > 10) {
            throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(),
                AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
        }
        SysOrg org = new SysOrg();
        org.setTenantId(tenantId);
        org.setParentId(req.parentOrgId() != null ? req.parentOrgId() : 0L);
        org.setOrgType(String.valueOf(req.orgType()));
        org.setCode(req.code());
        org.setName(req.orgName());
        org.setStatus(req.status() != null ? req.status() : 1);
        org.setSortOrder(req.sort());
        org.setLevel(level);
        org.setCreatedAt(LocalDateTime.now());
        org.setUpdatedAt(LocalDateTime.now());
        org.setDeleteFlag(0L);
        orgDomainService.insert(org);
        projectOrg(tenantId, org, "UPSERT");
        return org.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "ORG_UPDATE", targetType = "sys_org",
        targetId = "#req.id()", summary = "'update org ' + #req.id()")
    public void updateOrg(OrgUpdateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysOrg org = orgDomainService.selectValidById(tenantId, req.id());
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG, String.valueOf(req.id()),
            OrgOperationCodeMapper.resolve(org.getOrgType(), AdminOperationCode.UPDATE));
        // T-ACCESS-005 评审 P1：组织移动安全门禁与树结构校验
        Long newParentId = req.parentOrgId();
        if (newParentId != null && !Objects.equals(newParentId, org.getParentId())) {
            validateOrgMove(tenantId, req.id(), org, newParentId);
        }
        if (req.code() != null && !req.code().equals(org.getCode())
            && orgDomainService.findByCode(tenantId, req.code()) != null) {
            throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(),
                AdminErrorCode.ORG_CODE_EXISTS.getMessage());
        }
        // T-ACCESS-005 评审 P1：可选字段仅更新提供的字段（null 跳过，保留原值）
        if (req.orgName() != null) {
            org.setName(req.orgName());
        }
        if (req.code() != null) {
            org.setCode(req.code());
        }
        if (req.status() != null) {
            org.setStatus(req.status());
        }
        if (req.sort() != null) {
            org.setSortOrder(req.sort());
        }
        org.setUpdatedAt(LocalDateTime.now());
        orgDomainService.update(org);
        projectOrg(tenantId, org, "UPSERT");
    }

    /**
     * 组织移动校验：新父级存在性 + UPDATE 门禁 + 循环检测 + level 更新（含子树同步）。
     * <p>
     * T-ACCESS-005 评审 P1 修复：原实现只校验深度、无新父级门禁、无循环检测、
     * Long 引用比较、且移动后不更新 level（子树 level 陈旧）。
     * </p>
     */
    private void validateOrgMove(Long tenantId, Long orgId, SysOrg org, Long newParentId) {
        if (newParentId.equals(orgId)) {
            throw new BizException(AdminErrorCode.ORG_PARENT_CYCLE.getCode(),
                AdminErrorCode.ORG_PARENT_CYCLE.getMessage());
        }
        List<Long> descendants = orgDomainService.getDescendantIds(tenantId, orgId);
        if (descendants.contains(newParentId)) {
            throw new BizException(AdminErrorCode.ORG_PARENT_CYCLE.getCode(),
                AdminErrorCode.ORG_PARENT_CYCLE.getMessage());
        }
        int newLevel = 1;
        if (newParentId != 0L) {
            SysOrg newParent = orgDomainService.selectValidById(tenantId, newParentId);
            if (newParent == null) {
                throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                    "父组织不存在: orgId=" + newParentId);
            }
            // 新父级 UPDATE 门禁：防止把组织移动到调用者无权管理的节点下
            permissionValidator.checkInstanceLevel(
                AdminResourceType.ORG, String.valueOf(newParentId),
                OrgOperationCodeMapper.resolve(newParent.getOrgType(), AdminOperationCode.UPDATE));
            newLevel = newParent.getLevel() != null ? newParent.getLevel() + 1 : 1;
        }
        if (newLevel > 10) {
            throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(),
                AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
        }
        int oldLevel = org.getLevel() != null ? org.getLevel() : 1;
        int delta = newLevel - oldLevel;
        org.setLevel(newLevel);
        org.setParentId(newParentId);
        if (delta != 0 && !descendants.isEmpty()) {
            orgDomainService.batchUpdateLevel(tenantId, descendants, delta);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "ORG_DELETE", targetType = "sys_org",
        targetId = "#id", summary = "'delete org ' + #id")
    public void deleteOrg(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysOrg org = orgDomainService.selectValidById(tenantId, id);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG, String.valueOf(id),
            OrgOperationCodeMapper.resolve(org.getOrgType(), AdminOperationCode.DELETE));
        if (orgDomainService.hasChildren(tenantId, id)) {
            throw new BizException(AdminErrorCode.ORG_HAS_CHILDREN.getCode(),
                AdminErrorCode.ORG_HAS_CHILDREN.getMessage());
        }
        List<SysUserOrg> members = userOrgDomainService.findByOrgIds(tenantId, List.of(id));
        String roleTypeCode = OrgOperationCodeMapper.isPositionOrg(org.getOrgType()) ? "POSITION" : "ORG";
        // T-ACCESS-005 评审 P2：批量解绑（一次批量加载 + 一次批量软删），替代循环单条 unbind N+1
        List<LocalProjectionDomainService.UserOrgBindKey> unbindKeys = new java.util.ArrayList<>();
        for (SysUserOrg member : members) {
            unbindKeys.add(new LocalProjectionDomainService.UserOrgBindKey(
                member.getUserId(), id, roleTypeCode));
        }
        localProjectionDomainService.batchUnbindUserOrg(tenantId, unbindKeys);
        java.util.LinkedHashSet<Long> abstractUserIds = new java.util.LinkedHashSet<>();
        for (SysUserOrg member : members) {
            Long abstractUserId = localProjectionDomainService.findAdminUserId(tenantId, member.getUserId());
            userOrgDomainService.deleteByUserIdAndOrgId(tenantId, member.getUserId(), id);
            if (abstractUserId != null) {
                abstractUserIds.add(abstractUserId);
            }
        }
        if (!abstractUserIds.isEmpty()) {
            PermissionChangeContext.markUsers(tenantId, abstractUserIds);
        }
        orgDomainService.softDeleteBatch(tenantId, List.of(id));
        Long roleId = localProjectionDomainService.findAdminOrgRoleId(tenantId, id, org.getOrgType());
        localProjectionDomainService.deleteAdminOrg(tenantId, id, org.getOrgType());
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "abstract_role", roleId == null ? id : roleId, "DELETE", null, null, null,
                new Long[0], roleId == null ? new Long[]{} : new Long[]{roleId})));
        if (roleId != null) {
            PermissionChangeContext.markRoles(tenantId, roleId);
            PermissionChangeContext.markRoleSnapshots(tenantId, Set.of(roleId));
        }
    }

    private void projectOrg(Long tenantId, SysOrg org, String operation) {
        Long roleId = localProjectionDomainService.upsertAdminOrg(
            tenantId, org.getId(), org.getOrgType(), org.getName(), org.getParentId(),
            org.getStatus(), org.getSortOrder(), extraOrgType(org.getOrgType()));
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "abstract_role", roleId, operation, null, null, null, new Long[0], new Long[]{roleId})));
        PermissionChangeContext.markRoles(tenantId, roleId);
    }

    private String extraOrgType(String orgType) {
        try {
            return objectMapper.writeValueAsString(Map.of("orgType", orgType == null ? "" : orgType));
        } catch (JsonProcessingException e) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "serialize org extra failed", e);
        }
    }

    private static Long operatorId() {
        Long id = AccessRequestContext.getOperatorId();
        return id != null ? id : StpUtil.getLoginIdAsLong();
    }
}
