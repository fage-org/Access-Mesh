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
import cn.ac.fage.accessmesh.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserOrgServiceImpl implements UserOrgService {

    private final UserOrgDomainService userOrgDomainService;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final AdminPermissionValidator permissionValidator;

    public UserOrgServiceImpl(UserOrgDomainService userOrgDomainService,
                              OrgTreeConfigDomainService orgTreeConfigDomainService,
                              AdminPermissionValidator permissionValidator) {
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserToOrgs(UserOrgAssignReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        List<String> orgResourceCodes = req.orgIds().stream()
            .map(String::valueOf)
            .toList();
        permissionValidator.checkBatchInstanceLevel(
            AdminResourceType.ORG,
            orgResourceCodes,
            AdminOperationCode.UPDATE
        );

        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        for (SysOrgTreeConfig config : defaultConfigs) {
            if (Boolean.TRUE.equals(config.getSingleAssoc()) && req.orgIds().size() > 1) {
                throw new BizException(AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getCode(),
                    AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getMessage());
            }
        }

        userOrgDomainService.deleteByUserId(tenantId, req.userId());

        LocalDateTime now = LocalDateTime.now();
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
            userOrgDomainService.insertBatch(toInsert);
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

        userOrgDomainService.deleteByUserIdAndOrgId(tenantId, userId, orgId);
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

        userOrgDomainService.setPrimaryOrg(tenantId, userId, orgId);
    }

    @Override
    public List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return userOrgDomainService.getUserOrgBriefs(tenantId, userId);
    }
}
