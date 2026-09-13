package cn.ac.fage.accessmesh.access.org.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.org.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.access.user.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.access.org.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.org.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.engine.constant.OrgOperationCodeMapper;
import cn.ac.fage.accessmesh.access.org.service.UserOrgAppService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.UserOrgWriteAppService;
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
public class UserOrgAppServiceImpl implements UserOrgAppService {

    private final UserOrgDomainService userOrgDomainService;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final OrgDomainService orgDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final UserOrgWriteAppService userOrgWriteAppService;

    public UserOrgAppServiceImpl(UserOrgDomainService userOrgDomainService,
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
     * 契约依据：{@code docs/design/access-service-api-contract.md} §8.9
     * <ul>
     *   <li>非默认树关系：ORG:UPDATE@orgId 门禁</li>
     *   <li>默认树关系：USER:UPDATE@userId 门禁（按身份目录边界）</li>
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
     * 契约依据：{@code docs/design/access-service-api-contract.md} §8.10
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "USER_ORG_SET_PRIMARY", targetType = "sys_user_org",
        targetId = "#userId", summary = "'set primary org ' + #orgId + ' for user ' + #userId")
    public void setPrimaryOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();

        cn.ac.fage.accessmesh.access.org.entity.SysOrg targetOrg = orgDomainService.selectValidById(tenantId, orgId);
        if (targetOrg == null) {
            throw new BizException(AccessErrorCode.ORG_NOT_FOUND.getCode(),
                AccessErrorCode.ORG_NOT_FOUND.getMessage());
        }
        permissionValidator.checkInstanceLevel(
            ResourceTypeCode.ORG,
            String.valueOf(orgId),
            OrgOperationCodeMapper.resolveForUserOrg(targetOrg.getOrgType(), OperationCode.UPDATE)
        );

        // 首期主组织仅表示默认组织树下的主归属
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            throw new BizException(AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }

        List<Long> defaultOrgIds = resolveDefaultTreeOrgIds(tenantId, defaultConfigs);
        if (!defaultOrgIds.contains(orgId)) {
            throw new BizException(AccessErrorCode.PRIMARY_MUST_BE_IN_DEFAULT_TREE.getCode(),
                AccessErrorCode.PRIMARY_MUST_BE_IN_DEFAULT_TREE.getMessage());
        }

        boolean targetAssigned = userOrgDomainService.findByUserId(tenantId, userId).stream()
            .anyMatch(userOrg -> orgId.equals(userOrg.getOrgId()));
        if (!targetAssigned) {
            throw new BizException(AccessErrorCode.USER_ORG_RELATION_NOT_FOUND.getCode(),
                AccessErrorCode.USER_ORG_RELATION_NOT_FOUND.getMessage());
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
