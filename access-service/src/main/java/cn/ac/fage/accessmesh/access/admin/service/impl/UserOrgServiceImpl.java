package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.security.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.access.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.access.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.admin.support.UserOrgKeys;
import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskEnvelope;
import cn.ac.fage.accessmesh.common.exception.BizException;
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
public class UserOrgServiceImpl implements UserOrgService {

    private final UserOrgDomainService userOrgDomainService;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final OrgDomainService orgDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final SyncTaskDomainService syncTaskDomainService;
    private final SyncTaskBuilder syncTaskBuilder;

    public UserOrgServiceImpl(UserOrgDomainService userOrgDomainService,
                              OrgTreeConfigDomainService orgTreeConfigDomainService,
                              OrgDomainService orgDomainService,
                              AdminPermissionValidator permissionValidator,
                              SyncTaskDomainService syncTaskDomainService,
                              SyncTaskBuilder syncTaskBuilder) {
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.syncTaskDomainService = syncTaskDomainService;
        this.syncTaskBuilder = syncTaskBuilder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserToOrgs(UserOrgAssignReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        List<Long> requestedOrgIds = new ArrayList<>(new LinkedHashSet<>(req.orgIds()));
        if (requestedOrgIds.isEmpty()) {
            return;
        }

        // 一次性加载目标 orgs，按 orgType 分桶后用各自操作码批量校验（声明式映射，见 OrgOperationCodeMapper）
        Map<Long, cn.ac.fage.accessmesh.access.admin.entity.SysOrg> orgMap =
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

        // 默认树单关联约束
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        for (SysOrgTreeConfig config : defaultConfigs) {
            if (Boolean.TRUE.equals(config.getSingleAssoc()) && requestedOrgIds.size() > 1) {
                throw new BizException(AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getCode(),
                    AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getMessage());
            }
        }

        // 关系级追加：仅插入不存在的关系，禁止全量替换
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

            // Outbox: enqueue PERM_USER_ROLE_SYNC BIND envelopes for each new user-org assignment
            for (SysUserOrg assoc : toInsert) {
                cn.ac.fage.accessmesh.access.admin.entity.SysOrg org = orgMap.get(assoc.getOrgId());
                String roleTypeCode = isPositionOrg(org.getOrgType()) ? "POSITION" : "ORG";
                String relationKey = UserOrgKeys.relationKey(assoc.getOrgId());
                String treeRootExternalId = orgTreeConfigDomainService.resolveTreeRootExternalId(
                    tenantId, assoc.getOrgId());
                SyncTaskEnvelope env = syncTaskBuilder.userOrgBind(assoc.getUserId(), assoc.getOrgId(),
                    roleTypeCode, relationKey, treeRootExternalId);
                syncTaskDomainService.enqueue(tenantId, env);
            }
        }

        // 处理 primaryOrgId：若指定了 primaryOrgId 且非新增关系中，需额外设主
        if (req.primaryOrgId() != null && toInsert.stream().noneMatch(a -> a.getOrgId().equals(req.primaryOrgId()))) {
            // primaryOrgId 在已有关系中或刚插入的关系中，确保主标记正确
            // 已有关系的 primary 设定由 setPrimaryOrgInScope 处理
            List<Long> defaultOrgIds = resolveDefaultTreeOrgIds(tenantId, defaultConfigs);
            if (defaultOrgIds.contains(req.primaryOrgId())) {
                userOrgDomainService.setPrimaryOrgInScope(tenantId, req.userId(), req.primaryOrgId(), defaultOrgIds);
            }
        }
    }

    /**
     * 移除单条 user-org 关系。默认树关系按身份目录高危处理。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.3.3
     * <ul>
     *   <li>非默认树关系：ADMIN_ORG:UPDATE@orgId 门禁</li>
     *   <li>默认树关系：ADMIN_USER:UPDATE@userId 门禁（按身份目录边界）</li>
     *   <li>移除后默认树关系归 0 时拒绝（身份目录高危保护）</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserFromOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 加载 org 确定类型
        cn.ac.fage.accessmesh.access.admin.entity.SysOrg org = orgDomainService.selectValidById(tenantId, orgId);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                "user-org unbind: org not found, orgId=" + orgId);
        }

        // 判断该 org 是否属于默认树 → 决定门禁策略
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        List<Long> defaultTreeOrgIds = resolveDefaultTreeOrgIds(tenantId, defaultConfigs);
        boolean isDefaultTreeOrg = defaultTreeOrgIds.contains(orgId);

        if (isDefaultTreeOrg) {
            // 默认树关系 → 身份目录边界门禁：ADMIN_USER:UPDATE@userId
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(userId),
                AdminOperationCode.UPDATE
            );

            // 检查移除后用户在默认树是否还有归属关系
            List<SysUserOrg> userDefaultOrgs = userOrgDomainService.findByUserId(tenantId, userId).stream()
                .filter(uo -> defaultTreeOrgIds.contains(uo.getOrgId()) && !uo.getOrgId().equals(orgId))
                .collect(Collectors.toList());
            if (userDefaultOrgs.isEmpty()) {
                throw new BizException(AdminErrorCode.USER_LOSE_DEFAULT_TREE_HOME.getCode(),
                    AdminErrorCode.USER_LOSE_DEFAULT_TREE_HOME.getMessage());
            }
        } else {
            // 非默认树关系 → ADMIN_ORG:UPDATE@orgId（按 orgType 分发操作码）
            permissionValidator.checkInstanceLevel(
                AdminResourceType.ORG,
                String.valueOf(orgId),
                OrgOperationCodeMapper.resolveForUserOrg(org.getOrgType(), AdminOperationCode.UPDATE)
            );
        }

        userOrgDomainService.deleteByUserIdAndOrgId(tenantId, userId, orgId);

        // Outbox: enqueue PERM_USER_ROLE_SYNC UNBIND envelope
        String roleTypeCode = OrgOperationCodeMapper.isPositionOrg(org.getOrgType()) ? "POSITION" : "ORG";
        String relationKey = UserOrgKeys.relationKey(orgId);
        String treeRootExternalId = orgTreeConfigDomainService.resolveTreeRootExternalId(tenantId, orgId);
        SyncTaskEnvelope env = syncTaskBuilder.userOrgUnbind(userId, orgId, roleTypeCode,
            relationKey, treeRootExternalId);
        syncTaskDomainService.enqueue(tenantId, env);
    }

    /**
     * 设置用户主组织（首期仅允许默认组织树主归属）。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.3.4
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrimaryOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        cn.ac.fage.accessmesh.access.admin.entity.SysOrg targetOrg = orgDomainService.selectValidById(tenantId, orgId);
        if (targetOrg == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(orgId),
            OrgOperationCodeMapper.resolveForUserOrg(targetOrg.getOrgType(), AdminOperationCode.UPDATE)
        );

        // 首期主组织仅表示默认组织树下的主归属
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }

        List<Long> defaultOrgIds = resolveDefaultTreeOrgIds(tenantId, defaultConfigs);
        if (!defaultOrgIds.contains(orgId)) {
            throw new BizException(AdminErrorCode.PRIMARY_MUST_BE_IN_DEFAULT_TREE.getCode(),
                AdminErrorCode.PRIMARY_MUST_BE_IN_DEFAULT_TREE.getMessage());
        }

        boolean targetAssigned = userOrgDomainService.findByUserId(tenantId, userId).stream()
            .anyMatch(userOrg -> orgId.equals(userOrg.getOrgId()));
        if (!targetAssigned) {
            throw new BizException(AdminErrorCode.USER_ORG_RELATION_NOT_FOUND.getCode(),
                AdminErrorCode.USER_ORG_RELATION_NOT_FOUND.getMessage());
        }

        // 仅在默认树内切换主标记，不影响其他组织树的 is_primary
        userOrgDomainService.setPrimaryOrgInScope(tenantId, userId, orgId, defaultOrgIds);
    }

    @Override
    public List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return userOrgDomainService.getUserOrgBriefs(tenantId, userId);
    }

    /**
     * 判断 sys_org.orgType 是否为岗位类型。
     * <p>
     * 委托 {@link OrgOperationCodeMapper#isPositionOrg}。
     * </p>
     */
    private static boolean isPositionOrg(String orgType) {
        return OrgOperationCodeMapper.isPositionOrg(orgType);
    }

    /**
     * 解析默认组织树的全部组织 ID（含根）。
     */
    private List<Long> resolveDefaultTreeOrgIds(Long tenantId, List<SysOrgTreeConfig> defaultConfigs) {
        return defaultConfigs.stream()
            .map(SysOrgTreeConfig::getRootOrgId)
            .filter(rootOrgId -> rootOrgId != null)
            .flatMap(rootOrgId -> orgDomainService.getDescendantIdsIncludingSelf(tenantId, rootOrgId).stream())
            .distinct()
            .toList();
    }
}
