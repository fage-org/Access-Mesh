package cn.ac.fage.accessmesh.access.org.service.impl;

import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.org.dto.req.OrgPageReq;
import cn.ac.fage.accessmesh.access.org.dto.req.OrgQuery;
import cn.ac.fage.accessmesh.access.org.dto.resp.OrgResp;
import cn.ac.fage.accessmesh.access.org.entity.SysOrg;
import cn.ac.fage.accessmesh.access.org.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.org.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.org.mapper.SysOrgTreeConfigMapper;
import cn.ac.fage.accessmesh.access.org.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.access.org.service.OrgVisibilityQueryAppService;
import cn.ac.fage.accessmesh.access.org.service.OrgWriteAppService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PageResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-ACCESS-052 组织面实例准入单测：组织树 V∪anc 裁剪、组织分页可见交集下推、
 * org/users 目标组织可见性校验。端到端委派链以 DelegatedDirectoryClosurePgIT 为准。
 */
@ExtendWith(MockitoExtension.class)
class OrgAppServiceImplDelegatedDirectoryTest {

    private static final Long TENANT = 1L;

    @Mock private SysOrgMapper orgMapper;
    @Mock private SysOrgTreeConfigMapper treeConfigMapper;
    @Mock private OrgTreeConfigDomainService treeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private OrgWriteAppService orgWriteAppService;
    @Mock private SysUserOrgMapper userOrgMapper;
    @Mock private UserDomainService userDomainService;
    @Mock private OrgVisibilityQueryAppService orgVisibilityQueryService;

    private OrgAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgAppServiceImpl(orgMapper, treeConfigMapper, treeConfigDomainService,
            orgDomainService, permissionValidator, orgWriteAppService, userOrgMapper,
            userDomainService, orgVisibilityQueryService);
    }

    private SysOrg org(Long id, String orgType, Long parentId) {
        SysOrg o = new SysOrg();
        o.setId(id);
        o.setTenantId(TENANT);
        o.setOrgType(orgType);
        o.setParentId(parentId);
        o.setName("org-" + id);
        o.setStatus(1);
        o.setSortOrder(0);
        return o;
    }

    private void mockDefaultTree(Long rootOrgId) {
        SysOrgTreeConfig config = new SysOrgTreeConfig();
        config.setId(1L);
        config.setTenantId(TENANT);
        config.setRootOrgId(rootOrgId);
        config.setTreeType("ORG");
        when(treeConfigDomainService.findDefaultConfigs(eq(TENANT))).thenReturn(List.of(config));
    }

    @Test
    @DisplayName("T-ACCESS-052 组织树实例准入：类型级拒但可见子树非空 → 树=可见节点∪祖先链")
    void treeOrgs_instanceGrantee_seesVisibleSubtreeWithAncestors() {
        // 树形：root(1) > dept(2) > sub(3)；另一支 root(1) > other(4)
        SysOrg root = org(1L, "1", 0L);
        SysOrg dept = org(2L, "1", 1L);
        SysOrg sub = org(3L, "1", 2L);
        SysOrg other = org(4L, "1", 1L);
        mockDefaultTree(1L);
        when(orgMapper.selectValidOrgIds(TENANT)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(orgMapper.selectOrgsForTree(eq(TENANT), any(), any()))
            .thenReturn(List.of(root, dept, sub, other));
        when(permissionValidator.hasTypeLevel(ResourceTypeCode.ORG, OperationCode.VIEW)).thenReturn(false);
        // 部门成员管理员：仅 dept 子树（2、3）可见；other 支不可见
        when(orgVisibilityQueryService.filterVisibleOrgIds(eq(TENANT), eq(100L), anyCollection()))
            .thenReturn(Set.of(2L, 3L));

        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class);
             MockedStatic<OperatorContext> op = mockStatic(OperatorContext.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(TENANT);
            op.when(OperatorContext::getOperatorId).thenReturn(100L);

            List<OrgResp> tree = service.treeOrgs(
                new OrgQuery(null, null, null, 1, null, null, null));

            // 树：root（祖先骨架）> dept > sub；other 被裁
            assertEquals(1, tree.size());
            assertEquals(1L, tree.get(0).id());
            assertEquals(1, tree.get(0).children().size());
            assertEquals(2L, tree.get(0).children().get(0).id());
            assertEquals(3L, tree.get(0).children().get(0).children().get(0).id());
        }
    }

    @Test
    @DisplayName("T-ACCESS-052 组织树零可见 → 403（fail-closed）；类型级通过走原门禁不过滤")
    void treeOrgs_noVisibleOrg_throws403() {
        when(orgMapper.selectValidOrgIds(TENANT)).thenReturn(List.of(1L));
        when(permissionValidator.hasTypeLevel(ResourceTypeCode.ORG, OperationCode.VIEW)).thenReturn(false);
        when(orgVisibilityQueryService.filterVisibleOrgIds(eq(TENANT), eq(100L), anyCollection()))
            .thenReturn(Set.of());

        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class);
             MockedStatic<OperatorContext> op = mockStatic(OperatorContext.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(TENANT);
            op.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class, () -> service.treeOrgs(
                new OrgQuery(null, null, null, 1, null, null, null)));
        }
    }

    @Test
    @DisplayName("T-ACCESS-052 组织分页实例准入：可见交集下推（先过滤再分页/计数）")
    void pageOrgs_instanceGrantee_pushesVisibleIntersection() {
        when(permissionValidator.hasTypeLevel(ResourceTypeCode.ORG, OperationCode.VIEW)).thenReturn(false);
        when(orgMapper.selectValidOrgIds(TENANT)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(orgVisibilityQueryService.filterVisibleOrgIds(eq(TENANT), eq(100L), anyCollection()))
            .thenReturn(Set.of(2L, 3L));
        when(orgMapper.countOrgsByCondition(eq(TENANT), any(), eq("1"), any(), eq(Set.of(2L, 3L))))
            .thenReturn(2L);

        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class);
             MockedStatic<OperatorContext> op = mockStatic(OperatorContext.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(TENANT);
            op.when(OperatorContext::getOperatorId).thenReturn(100L);

            PageResp<OrgResp> page = service.pageOrgs(new OrgPageReq(1, 10, null, null, 1, null, null, null));

            assertEquals(2L, page.total());
        }
        verify(orgMapper).countOrgsByCondition(eq(TENANT), any(), eq("1"), any(), eq(Set.of(2L, 3L)));
    }

    @Test
    @DisplayName("T-ACCESS-052 org/users 补目标组织可见性校验：不可见 orgId → 11006（防成员名单探测）")
    void listOrgUsers_invisibleTargetOrg_rejected() {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class);
             MockedStatic<OperatorContext> op = mockStatic(OperatorContext.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(TENANT);
            op.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(orgVisibilityQueryService.filterVisibleOrgIds(eq(TENANT), eq(100L), eq(Set.of(9L))))
                .thenReturn(Set.of());

            BizException ex = assertThrows(BizException.class, () -> service.listOrgUsers(9L));
            assertEquals(10101, ex.getErrorCode());
        }
        verify(userOrgMapper, never()).selectListByQuery(any());
    }
}
