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
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
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
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final AuditDomainService auditDomainService;
    private final ObjectMapper objectMapper;

    public OrgWriteAppServiceImpl(OrgDomainService orgDomainService,
                                  UserOrgDomainService userOrgDomainService,
                                  OrgTreeConfigDomainService orgTreeConfigDomainService,
                                  AdminPermissionValidator permissionValidator,
                                  LocalProjectionDomainService localProjectionDomainService,
                                  AuditDomainService auditDomainService,
                                  ObjectMapper objectMapper) {
        this.orgDomainService = orgDomainService;
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
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
        // orgType 仅允许 1=组织 / 2=岗位（契约 §4.2.4；未知类型会被操作码/投影按普通组织处理，必须拒绝）
        if (req.orgType() == null || (req.orgType() != 1 && req.orgType() != 2)) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "orgType 必须为 1（组织）或 2（岗位）");
        }
        String orgType = String.valueOf(req.orgType());
        Long tenantId = TenantContextHolder.getTenantId();
        // 契约 §4.2.4 互斥门禁——顶级用类型级 CREATE，子级只用父节点实例级 UPDATE
        // （不再无条件先校验类型级 CREATE，避免误拒绝可管理父节点但无租户级 CREATE 的局部管理员）；
        // 编码存在性探测（findByCode）在鉴权之后执行，不向未授权调用者暴露编码是否存在
        SysOrg parent = null;
        if (req.parentOrgId() != null && req.parentOrgId() != 0L) {
            parent = orgDomainService.selectValidById(tenantId, req.parentOrgId());
            if (parent == null) {
                throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                    "父组织不存在: orgId=" + req.parentOrgId());
            }
            permissionValidator.checkInstanceLevel(
                AdminResourceType.ORG, String.valueOf(req.parentOrgId()),
                OrgOperationCodeMapper.resolve(parent.getOrgType(), AdminOperationCode.UPDATE));
            // 父节点必须可解析到树（游离拒绝）
            orgTreeConfigDomainService.resolveTreeRootExternalId(tenantId, req.parentOrgId());
        } else {
            permissionValidator.checkTypeLevel(
                AdminResourceType.ORG, OrgOperationCodeMapper.resolve(orgType, AdminOperationCode.CREATE));
        }
        // 岗位拓扑约束：岗位必须有父（非顶级）且父必须是普通组织；任何节点不能挂在岗位下
        validatePositionTopology(orgType, req.parentOrgId(), parent);
        if (orgDomainService.findByCode(tenantId, req.code()) != null) {
            throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(),
                AdminErrorCode.ORG_CODE_EXISTS.getMessage());
        }
        int level = parent != null && parent.getLevel() != null ? parent.getLevel() + 1 : 1;
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
        projectOrg(tenantId, org, "UPSERT", parent != null ? parent.getOrgType() : null);
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
        // 组织移动安全门禁与树结构校验
        Long newParentId = req.parentOrgId();
        Long oldParentId = org.getParentId();
        if (newParentId != null && !Objects.equals(newParentId, oldParentId)) {
            validateOrgMove(tenantId, req.id(), org, newParentId);
            // 岗位（POSITION）移动后迁移已有成员 user_role.relation_id
            // （旧所属组织 → 新所属组织），否则后续解绑按新三元组匹配不到旧记录导致投影残留
            if (OrgOperationCodeMapper.isPositionOrg(org.getOrgType())) {
                java.util.Set<Long> affectedUsers = localProjectionDomainService.migratePositionRelation(
                    tenantId, org.getId(), oldParentId, newParentId);
                if (!affectedUsers.isEmpty()) {
                    PermissionChangeContext.markUsers(tenantId, affectedUsers);
                }
            }
        }
        if (req.code() != null && !req.code().equals(org.getCode())
            && orgDomainService.findByCode(tenantId, req.code()) != null) {
            throw new BizException(AdminErrorCode.ORG_CODE_EXISTS.getCode(),
                AdminErrorCode.ORG_CODE_EXISTS.getMessage());
        }
        // 可选字段仅更新提供的字段（null 跳过，保留原值）
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
        projectOrg(tenantId, org, "UPSERT", resolveParentOrgType(tenantId, org));
    }

    /**
     * 岗位拓扑校验（创建路径）：岗位（orgType=2）必须作为普通组织（orgType=1）的直接子节点，
     * 且岗位自身不能拥有下级节点。对齐契约：一体树中岗位作为所属组织的子节点挂入同一树，岗位无下级。
     * 父节点必须是普通组织（orgType=1/ORG/存量 null），岗位与未知类型（如 orgType=3）均拒绝。
     */
    private void validatePositionTopology(String orgType, Long parentOrgId, SysOrg parent) {
        if (OrgOperationCodeMapper.isPositionOrg(orgType)) {
            if (parentOrgId == null || parentOrgId == 0L) {
                throw new BizException(AdminErrorCode.ORG_POSITION_TOPOLOGY_INVALID.getCode(),
                    AdminErrorCode.ORG_POSITION_TOPOLOGY_INVALID.getMessage());
            }
        }
        if (parent != null && !isRegularOrgType(parent.getOrgType())) {
            throw new BizException(AdminErrorCode.ORG_POSITION_TOPOLOGY_INVALID.getCode(),
                AdminErrorCode.ORG_POSITION_TOPOLOGY_INVALID.getMessage());
        }
    }

    /** 父节点必须是普通组织（orgType=1 或历史语义 ORG；岗位与未知类型均拒绝）。 */
    private static boolean isRegularOrgType(String orgType) {
        if (orgType == null || orgType.isBlank()) {
            return true; // 存量 null 按普通组织
        }
        return "1".equals(orgType) || "ORG".equalsIgnoreCase(orgType);
    }

    /**
     * 解析父节点实际 orgType（ORG 父 + POSITION 子时父角色类型正确投影）。
     */
    private String resolveParentOrgType(Long tenantId, SysOrg org) {
        if (org.getParentId() == null || org.getParentId() == 0L) {
            return null;
        }
        SysOrg parent = orgDomainService.selectValidById(tenantId, org.getParentId());
        return parent != null ? parent.getOrgType() : null;
    }

    /**
     * 组织移动校验：新父级存在性 + UPDATE 门禁 + 循环检测 + 跨树校验 + 深度校验 + level 更新（含子树同步）。
     * <p>
     * （用户决策：严格跨树+禁顶级移动）：跨树比较（resolveTreeRootExternalId）、
     * 移动到顶级拒绝（树根由组织树配置管理）、子树最深节点移动后不超过 10 层。
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
        // 用户决策：移动到顶级（parentOrgId=0）拒绝——树根由 SysOrgTreeConfig 管理，
        // 移动为游离根会破坏树归属（原实现静默 level=1）
        if (newParentId == 0L) {
            throw new BizException(AdminErrorCode.ORG_MOVE_TOP_LEVEL_FORBIDDEN.getCode(),
                AdminErrorCode.ORG_MOVE_TOP_LEVEL_FORBIDDEN.getMessage());
        }
        SysOrg newParent = orgDomainService.selectValidById(tenantId, newParentId);
        if (newParent == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                "父组织不存在: orgId=" + newParentId);
        }
        // 新父级 UPDATE 门禁：防止把组织移动到调用者无权管理的节点下
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG, String.valueOf(newParentId),
            OrgOperationCodeMapper.resolve(newParent.getOrgType(), AdminOperationCode.UPDATE));
        // 岗位拓扑约束：新父必须是普通组织（岗位自身无下级——岗位下挂节点拒绝；
        // 未知类型（如 orgType=3）也不能作为父节点；岗位移动到组织下满足"岗位必须作为组织的直接子节点"）
        if (!isRegularOrgType(newParent.getOrgType())) {
            throw new BizException(AdminErrorCode.ORG_POSITION_TOPOLOGY_INVALID.getCode(),
                AdminErrorCode.ORG_POSITION_TOPOLOGY_INVALID.getMessage());
        }
        // 用户决策：跨树移动拒绝（原树 ≠ 目标树，ORG_CROSS_TREE_MOVE 对齐契约 CROSS_TREE_MOVE_FORBIDDEN）
        String oldRoot = orgTreeConfigDomainService.resolveTreeRootExternalId(tenantId, orgId);
        String newRoot = orgTreeConfigDomainService.resolveTreeRootExternalId(tenantId, newParentId);
        if (!oldRoot.equals(newRoot)) {
            throw new BizException(AdminErrorCode.ORG_CROSS_TREE_MOVE.getCode(),
                AdminErrorCode.ORG_CROSS_TREE_MOVE.getMessage());
        }
        int newLevel = newParent.getLevel() != null ? newParent.getLevel() + 1 : 1;
        if (newLevel > 10) {
            throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(),
                AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
        }
        int oldLevel = org.getLevel() != null ? org.getLevel() : 1;
        int delta = newLevel - oldLevel;
        // 移动后子树最深节点不得超过 10 层（仅检查移动节点会漏检深子树）
        if (delta > 0 && !descendants.isEmpty()) {
            List<SysOrg> subtreeOrgs = orgDomainService.selectValidByIds(
                tenantId, new java.util.HashSet<>(descendants));
            int maxSubLevel = subtreeOrgs.stream()
                .mapToInt(o -> o.getLevel() != null ? o.getLevel() : 1)
                .max().orElse(0);
            if (maxSubLevel + delta > 10) {
                throw new BizException(AdminErrorCode.ORG_LEVEL_EXCEEDED.getCode(),
                    AdminErrorCode.ORG_LEVEL_EXCEEDED.getMessage());
            }
        }
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
        // 批量解绑（一次批量加载 + 一次批量软删），替代循环单条 unbind N+1；
        // POSITION 成员 relation 指向所属组织（岗位的 parentId）
        List<LocalProjectionDomainService.UserOrgBindKey> unbindKeys = new java.util.ArrayList<>();
        for (SysUserOrg member : members) {
            unbindKeys.add(new LocalProjectionDomainService.UserOrgBindKey(
                member.getUserId(), id, roleTypeCode, org.getParentId()));
        }
        localProjectionDomainService.batchUnbindUserOrg(tenantId, unbindKeys);
        // 批量解析 abstract_user.id，替代循环单条 find
        java.util.LinkedHashSet<Long> abstractUserIds = new java.util.LinkedHashSet<>(
            localProjectionDomainService.batchFindAdminUserIds(
                tenantId, members.stream().map(SysUserOrg::getUserId).collect(java.util.stream.Collectors.toSet()))
                .values());
        // 批量删除成员关系（单条 SQL），替代循环单条 deleteByUserIdAndOrgId
        if (!members.isEmpty()) {
            userOrgDomainService.deleteByUserIdsAndOrgId(tenantId,
                members.stream().map(SysUserOrg::getUserId).collect(java.util.stream.Collectors.toSet()), id);
        }
        if (!abstractUserIds.isEmpty()) {
            PermissionChangeContext.markUsers(tenantId, abstractUserIds);
        }
        orgDomainService.softDeleteBatch(tenantId, List.of(id));
        Long roleId = localProjectionDomainService.findAdminOrgRoleId(tenantId, id, org.getOrgType());
        localProjectionDomainService.deleteAdminOrg(tenantId, id, org.getOrgType());
        // entity_id 记录投影主键；投影缺失时记 null（不再冒用 sys_org.id）
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "abstract_role", roleId, "DELETE", null, null, null,
                new Long[0], roleId == null ? new Long[]{} : new Long[]{roleId})));
        if (roleId != null) {
            PermissionChangeContext.markRoles(tenantId, roleId);
            PermissionChangeContext.markRoleSnapshots(tenantId, Set.of(roleId));
        }
    }

    private void projectOrg(Long tenantId, SysOrg org, String operation, String parentOrgType) {
        Long roleId = localProjectionDomainService.upsertAdminOrg(
            tenantId, org.getId(), org.getOrgType(), org.getName(), org.getParentId(),
            parentOrgType, org.getStatus(), org.getSortOrder(), extraOrgType(org.getOrgType()));
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
