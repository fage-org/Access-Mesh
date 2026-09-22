package cn.ac.fage.accessmesh.access.org.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.org.dto.req.UserOrgAssignReq;
import cn.ac.fage.accessmesh.access.user.dto.resp.UserPageItemResp;
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
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
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
    private final TreeWriteLockSupport treeWriteLockSupport;

    public UserOrgAppServiceImpl(UserOrgDomainService userOrgDomainService,
                              OrgTreeConfigDomainService orgTreeConfigDomainService,
                              OrgDomainService orgDomainService,
                              AdminPermissionValidator permissionValidator,
                              UserOrgWriteAppService userOrgWriteAppService,
                              TreeWriteLockSupport treeWriteLockSupport) {
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.userOrgWriteAppService = userOrgWriteAppService;
        this.treeWriteLockSupport = treeWriteLockSupport;
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
     *   <li>非默认树关系：按目标 orgType 解析成员动作码（MANAGE_MEMBER/ASSIGN_POSITION_USER，v1.4）</li>
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

        // SYS_ORG 树锁（claude 外评 P2，与 assign/remove 同族补齐）：设主的守卫读
        //（默认树范围+目标归属）与 is_primary 双写全部入锁——否则与并发 deleteOrg
        //（持同锁级联软删归属行）交错时，清旧主 UPDATE 因 delete_flag=0 滤掉已删行、
        // 置新主 UPDATE 命中 0 行，接口 200 但用户默认树主归属静默丢失；锁先于守卫首读
        //（resolveDefaultTreeOrgIds），org 存在性解析与门禁非守卫输入留在锁前
        //（removeUserFromOrg 同序先例）
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.SYS_ORG);
        // 首期主组织仅表示默认组织树下的主归属；默认树范围解析换绑领域共享入口
        //（Q-025 随 T-ORG-003 收敛——原私有副本与共享入口并存且多配置展开行为有差，
        // 读面 11014 判定与写面守卫同源；退化根（配置在而根组织失联）由共享入口
        // 空返回统一折算 ORG_TREE_CONFIG_NOT_FOUND，正常形态两实现等价）
        List<Long> defaultOrgIds = orgTreeConfigDomainService.resolveDefaultTreeOrgIds(tenantId);
        if (defaultOrgIds.isEmpty()) {
            throw new BizException(AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getCode(),
                AccessErrorCode.ORG_TREE_CONFIG_NOT_FOUND.getMessage());
        }
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
}
