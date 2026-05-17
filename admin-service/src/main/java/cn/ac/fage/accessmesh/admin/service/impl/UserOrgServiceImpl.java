package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.service.UserOrgService;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import cn.dev33.satoken.stp.StpUtil;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * 用户组织关联服务实现类
 * <p>
 * 提供用户与组织关联的管理功能，包括批量分配、移除关联、设置主组织等。
 * 用户修改自己的组织关联无需权限校验（自我修改豁免）。
 * 根据组织树配置校验单组织关联限制（singleAssoc=true时不允许多组织）。
 * 使用批量插入优化性能。
 * </p>
 */
@Service
public class UserOrgServiceImpl implements UserOrgService {

    private final UserOrgDomainService userOrgDomainService;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param userOrgDomainService 用户组织关联领域服务
     * @param orgTreeConfigDomainService 组织树配置领域服务，校验单组织限制
     * @param permissionValidator 权限校验器
     */
    public UserOrgServiceImpl(UserOrgDomainService userOrgDomainService,
                              OrgTreeConfigDomainService orgTreeConfigDomainService,
                              AdminPermissionValidator permissionValidator) {
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 批量分配用户到组织
     * <p>
     * 将用户分配到多个组织，先删除现有关联再批量创建新关联。
     * 用户修改自己的组织关联无需权限校验（自我修改豁免）。
     * 根据组织树配置校验单组织关联限制。
     * 使用批量插入优化性能，避免N次插入。
     * </p>
     *
     * @param req 用户组织分配请求，包含用户ID和组织ID列表、主组织ID
     * @throws BizException 单组织关联限制冲突
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserToOrgs(UserOrgAssignReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = StpUtil.getLoginIdAsLong();

        // 自我分配豁免：用户可分配自己的组织无需权限检查
        if (!req.userId().equals(operatorId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(req.userId()),
                AdminOperationCode.UPDATE
            );
        }

        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        for (SysOrgTreeConfig config : defaultConfigs) {
            if (Boolean.TRUE.equals(config.getSingleAssoc()) && req.orgIds().size() > 1) {
                throw new BizException(AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getCode(),
                    AdminErrorCode.ORG_SINGLE_ASSOC_VIOLATION.getMessage());
            }
        }

        userOrgDomainService.deleteByUserId(tenantId, req.userId());

        LocalDateTime now = LocalDateTime.now();
        // 性能优化：收集实体进行批量插入
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

    /**
     * 移除用户与组织的关联
     * <p>
     * 删除单个用户组织关联记录。
     * 用户移除自己的组织关联无需权限校验（自我修改豁免）。
     * </p>
     *
     * @param userId 用户ID
     * @param orgId 组织ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserFromOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = StpUtil.getLoginIdAsLong();

        // 自我移除豁免：用户可移除自己的组织关联
        if (!userId.equals(operatorId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(userId),
                AdminOperationCode.UPDATE
            );
        }

        userOrgDomainService.deleteByUserIdAndOrgId(tenantId, userId, orgId);
    }

    /**
     * 设置用户主组织
     * <p>
     * 将指定组织设为用户的主组织（用于默认组织选择）。
     * 用户修改自己的主组织无需权限校验（自我修改豁免）。
     * </p>
     *
     * @param userId 用户ID
     * @param orgId 组织ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrimaryOrg(Long userId, Long orgId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = StpUtil.getLoginIdAsLong();

        // 自我修改豁免：用户可设置自己的主组织
        if (!userId.equals(operatorId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(userId),
                AdminOperationCode.UPDATE
            );
        }

        userOrgDomainService.setPrimaryOrg(tenantId, userId, orgId);
    }

    /**
     * 获取用户关联的组织列表
     * <p>
     * 查询用户关联的所有组织简要信息，标记主组织。
     * </p>
     *
     * @param userId 用户ID
     * @return 组织简要信息列表
     */
    @Override
    public List<UserPageItemResp.OrgBrief> getUserOrgs(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return userOrgDomainService.getUserOrgBriefs(tenantId, userId);
    }
}