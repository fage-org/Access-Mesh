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
import cn.ac.fage.accessmesh.access.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.UserOrgWriteAppService;
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
    private final UserOrgWriteAppService userOrgWriteAppService;

    public UserOrgServiceImpl(UserOrgDomainService userOrgDomainService,
                              OrgTreeConfigDomainService orgTreeConfigDomainService,
                              OrgDomainService orgDomainService,
                              AdminPermissionValidator permissionValidator,
                              UserOrgWriteAppService userOrgWriteAppService) {
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.userOrgWriteAppService = userOrgWriteAppService;
    }

    @Override
    public void assignUserToOrgs(UserOrgAssignReq req) {
        userOrgWriteAppService.assignUserToOrgs(req);
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
    public void removeUserFromOrg(Long userId, Long orgId) {
        userOrgWriteAppService.removeUserFromOrg(userId, orgId);
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
