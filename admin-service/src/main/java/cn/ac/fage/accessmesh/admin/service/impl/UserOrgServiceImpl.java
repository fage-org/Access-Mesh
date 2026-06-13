package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskEnvelope;
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
        List<String> orgResourceCodes = requestedOrgIds.stream()
            .map(String::valueOf)
            .toList();
        permissionValidator.checkBatchInstanceLevel(
            AdminResourceType.ORG,
            orgResourceCodes,
            AdminOperationCode.UPDATE
        );

        /*
         * 设计约束：
         * - 默认/非默认树约束见 default-org-tree-user-lifecycle.md 第 1-2 节。
         * - 该方法后续必须改为关系级追加或显式树内替换，不能删除用户
         *   在默认树或其他树下的全部关系。
         * - user-org 变更还必须同步为 permission-center user_role（使用业务键）。
         * 当前实现保留旧的全量替换行为，仅作为待改造点标注。
         */
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

            // Outbox: enqueue PERM_USER_ROLE_SYNC BIND envelopes for each new user-org assignment
            // 一次批量加载 SysOrg，按 orgType 解析 roleTypeCode（ORG/POSITION），避免 builder 端做静默二级 fallback
            Set<Long> insertOrgIds = toInsert.stream().map(SysUserOrg::getOrgId).collect(Collectors.toSet());
            Map<Long, cn.ac.fage.accessmesh.admin.entity.SysOrg> orgMap =
                orgDomainService.batchSelectValidByIdsMap(tenantId, insertOrgIds);
            for (SysUserOrg assoc : toInsert) {
                cn.ac.fage.accessmesh.admin.entity.SysOrg org = orgMap.get(assoc.getOrgId());
                if (org == null) {
                    throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                        "user-org bind sync: org not found, orgId=" + assoc.getOrgId());
                }
                String roleTypeCode = "POSITION".equalsIgnoreCase(org.getOrgType()) ? "POSITION" : "ORG";
                String relationKey = roleTypeCode + ":" + assoc.getOrgId();
                String treeRootExternalId = orgTreeConfigDomainService.resolveTreeRootExternalId(
                    tenantId, assoc.getOrgId());
                SyncTaskEnvelope env = syncTaskBuilder.userOrgBind(assoc.getUserId(), assoc.getOrgId(),
                    roleTypeCode, relationKey, treeRootExternalId);
                syncTaskDomainService.enqueue(tenantId, env);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserFromOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(orgId),
            AdminOperationCode.UPDATE
        );

        // 非默认树移除成员只应删除关系并回收对应 user_role，不应影响用户生命周期。
        userOrgDomainService.deleteByUserIdAndOrgId(tenantId, userId, orgId);

        // Outbox: enqueue PERM_USER_ROLE_SYNC UNBIND envelope
        // 解析 roleTypeCode 必须读 sys_org.orgType；与 BIND 链路保持对称，确保 business_key 匹配
        cn.ac.fage.accessmesh.admin.entity.SysOrg org = orgDomainService.selectValidById(tenantId, orgId);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                "user-org unbind sync: org not found, orgId=" + orgId);
        }
        String roleTypeCode = "POSITION".equalsIgnoreCase(org.getOrgType()) ? "POSITION" : "ORG";
        String relationKey = roleTypeCode + ":" + orgId;
        String treeRootExternalId = orgTreeConfigDomainService.resolveTreeRootExternalId(tenantId, orgId);
        SyncTaskEnvelope env = syncTaskBuilder.userOrgUnbind(userId, orgId, roleTypeCode,
            relationKey, treeRootExternalId);
        syncTaskDomainService.enqueue(tenantId, env);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrimaryOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(orgId),
            AdminOperationCode.UPDATE
        );

        // 首期主组织仅表示默认组织树下的主归属，后续实现需避免影响其他组织树关系。
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            throw new BizException(AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                AdminErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }

        List<Long> defaultOrgIds = defaultConfigs.stream()
            .map(SysOrgTreeConfig::getRootOrgId)
            .filter(rootOrgId -> rootOrgId != null)
            .flatMap(rootOrgId -> orgDomainService.getDescendantIdsIncludingSelf(tenantId, rootOrgId).stream())
            .distinct()
            .toList();
        if (!defaultOrgIds.contains(orgId)) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(), "primary org must belong to default org tree");
        }

        boolean targetAssigned = userOrgDomainService.findByUserId(tenantId, userId).stream()
            .anyMatch(userOrg -> orgId.equals(userOrg.getOrgId()));
        if (!targetAssigned) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(), "user is not assigned to target org");
        }

        userOrgDomainService.setPrimaryOrgInScope(tenantId, userId, orgId, defaultOrgIds);
    }

    @Override
    public List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return userOrgDomainService.getUserOrgBriefs(tenantId, userId);
    }
}
