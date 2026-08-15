package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.impl.OrgWriteAppServiceImpl;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

/**
 * T-ACCESS-005 评审 P1 修复：组织移动安全门禁（新父级存在性 + UPDATE 权限 + 循环检测 + level 子树同步）
 * 与可选字段部分更新语义（null 跳过）。
 */
@ExtendWith(MockitoExtension.class)
class OrgWriteAppServiceTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;
    private static final Long ORG_ID = 10L;

    @Mock private OrgDomainService orgDomainService;
    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private AuditDomainService auditDomainService;

    private OrgWriteAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgWriteAppServiceImpl(
            orgDomainService,
            userOrgDomainService,
            orgTreeConfigDomainService,
            permissionValidator,
            localProjectionDomainService,
            auditDomainService,
            new ObjectMapper()
        );
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    private SysOrg org(String code, String orgType, Integer level) {
        SysOrg org = new SysOrg();
        org.setId(ORG_ID);
        org.setTenantId(TENANT);
        org.setParentId(1L);
        org.setOrgType(orgType);
        org.setCode(code);
        org.setName("旧名称");
        org.setStatus(1);
        org.setSortOrder(5);
        org.setLevel(level);
        return org;
    }

    @Test
    @DisplayName("移动到不存在的父组织 → 拒绝（ORG_NOT_FOUND）")
    void moveToMissingParentRejected() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));
        when(orgDomainService.getDescendantIds(TENANT, ORG_ID)).thenReturn(List.of());
        when(orgDomainService.selectValidById(TENANT, 999L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateOrg(new OrgUpdateReq(ORG_ID, null, 999L, null, null, null)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("父组织不存在");
    }

    @Test
    @DisplayName("移动到自己的子孙节点 → 拒绝（ORG_PARENT_CYCLE）")
    void moveToDescendantRejected() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));
        when(orgDomainService.getDescendantIds(TENANT, ORG_ID)).thenReturn(List.of(30L, 31L));

        assertThatThrownBy(() -> service.updateOrg(new OrgUpdateReq(ORG_ID, null, 30L, null, null, null)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("子孙节点");
        verify(orgDomainService, never()).update(any(SysOrg.class));
    }

    @Test
    @DisplayName("移动到自身 → 拒绝（ORG_PARENT_CYCLE）")
    void moveToSelfRejected() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));

        assertThatThrownBy(() -> service.updateOrg(new OrgUpdateReq(ORG_ID, null, ORG_ID, null, null, null)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("子孙节点");
    }

    @Test
    @DisplayName("移动时校验新父级 UPDATE 权限（无权限 → SecurityException）")
    void moveChecksNewParentPermission() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));
        when(orgDomainService.getDescendantIds(TENANT, ORG_ID)).thenReturn(List.of());
        SysOrg newParent = new SysOrg();
        newParent.setId(20L);
        newParent.setOrgType("1");
        newParent.setLevel(3);
        when(orgDomainService.selectValidById(TENANT, 20L)).thenReturn(newParent);
        // lenient：org 自身 UPDATE 校验（"10"）先执行，与新父级 stub（"20"）参数不同，strict 模式需放宽
        org.mockito.Mockito.lenient().doThrow(new SecurityException("denied"))
            .when(permissionValidator).checkInstanceLevel(eq(AdminResourceType.ORG), eq("20"), anyString());

        assertThatThrownBy(() -> service.updateOrg(new OrgUpdateReq(ORG_ID, null, 20L, null, null, null)))
            .isInstanceOf(SecurityException.class);
        verify(orgDomainService, never()).update(any(SysOrg.class));
        verify(orgDomainService, never()).batchUpdateLevel(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("移动成功：同树校验通过，更新自身 level + 子树 level 增量同步")
    void moveUpdatesLevelAndSubtree() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));
        when(orgDomainService.getDescendantIds(TENANT, ORG_ID)).thenReturn(List.of(30L));
        SysOrg newParent = new SysOrg();
        newParent.setId(20L);
        newParent.setOrgType("1");
        newParent.setLevel(4);
        when(orgDomainService.selectValidById(TENANT, 20L)).thenReturn(newParent);
        // 同树（九轮评审 P1：跨树校验）
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, ORG_ID)).thenReturn("1");
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, 20L)).thenReturn("1");
        // 子树最深 level 2 + delta 3 = 5 ≤ 10（九轮评审 P1：深度校验）
        SysOrg subtreeOrg = new SysOrg();
        subtreeOrg.setId(30L);
        subtreeOrg.setLevel(2);
        when(orgDomainService.selectValidByIds(eq(TENANT), any())).thenReturn(List.of(subtreeOrg));

        service.updateOrg(new OrgUpdateReq(ORG_ID, null, 20L, null, null, null));

        ArgumentCaptor<SysOrg> captor = ArgumentCaptor.forClass(SysOrg.class);
        verify(orgDomainService).update(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(5); // 新父级 level 4 + 1
        assertThat(captor.getValue().getParentId()).isEqualTo(20L);
        verify(orgDomainService).batchUpdateLevel(TENANT, List.of(30L), 3); // 5 - 2
    }

    @Test
    @DisplayName("移动到顶级 → 拒绝（九轮决策：树根由配置管理）")
    void moveToTopLevelRejected() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));

        assertThatThrownBy(() -> service.updateOrg(new OrgUpdateReq(ORG_ID, null, 0L, null, null, null)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("顶级");
        verify(orgDomainService, never()).update(any(SysOrg.class));
    }

    @Test
    @DisplayName("跨树移动 → 拒绝（ORG_CROSS_TREE_MOVE）")
    void moveAcrossTreesRejected() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));
        when(orgDomainService.getDescendantIds(TENANT, ORG_ID)).thenReturn(List.of());
        SysOrg newParent = new SysOrg();
        newParent.setId(20L);
        newParent.setOrgType("1");
        newParent.setLevel(1);
        when(orgDomainService.selectValidById(TENANT, 20L)).thenReturn(newParent);
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, ORG_ID)).thenReturn("1");
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, 20L)).thenReturn("2");

        assertThatThrownBy(() -> service.updateOrg(new OrgUpdateReq(ORG_ID, null, 20L, null, null, null)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("跨树");
        verify(orgDomainService, never()).update(any(SysOrg.class));
    }

    @Test
    @DisplayName("移动后子树最深节点超过 10 层 → 拒绝")
    void moveWithDeepSubtreeRejected() {
        // org level 4，子树最深 10；移动到 level 4 父（newLevel 5，delta 1）→ 最深 11 > 10 拒绝
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 4));
        when(orgDomainService.getDescendantIds(TENANT, ORG_ID)).thenReturn(List.of(30L));
        SysOrg newParent = new SysOrg();
        newParent.setId(20L);
        newParent.setOrgType("1");
        newParent.setLevel(4);
        when(orgDomainService.selectValidById(TENANT, 20L)).thenReturn(newParent);
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, ORG_ID)).thenReturn("1");
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, 20L)).thenReturn("1");
        SysOrg deepOrg = new SysOrg();
        deepOrg.setId(30L);
        deepOrg.setLevel(10);
        when(orgDomainService.selectValidByIds(eq(TENANT), any())).thenReturn(List.of(deepOrg));

        assertThatThrownBy(() -> service.updateOrg(new OrgUpdateReq(ORG_ID, null, 20L, null, null, null)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("10层");
        verify(orgDomainService, never()).update(any(SysOrg.class));
        verify(orgDomainService, never()).batchUpdateLevel(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("创建子组织：父不存在 → 拒绝（九轮 P1）")
    void createOrgWithMissingParentRejected() {
        when(orgDomainService.selectValidById(TENANT, 999L)).thenReturn(null);

        assertThatThrownBy(() -> service.createOrg(orgCreateReq(999L)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("父组织不存在");
        verify(orgDomainService, never()).insert(any(SysOrg.class));
    }

    @Test
    @DisplayName("创建子组织：校验父节点 UPDATE 门禁（九轮 P1）")
    void createOrgChecksParentPermission() {
        SysOrg parent = new SysOrg();
        parent.setId(999L);
        parent.setOrgType("1");
        parent.setLevel(1);
        when(orgDomainService.selectValidById(TENANT, 999L)).thenReturn(parent);
        // lenient：自身 CREATE 校验（类型级）先执行；父 UPDATE 门禁在树校验前抛异常（tree stub 无需）
        org.mockito.Mockito.lenient().doThrow(new SecurityException("denied"))
            .when(permissionValidator).checkInstanceLevel(eq(AdminResourceType.ORG), eq("999"), anyString());

        assertThatThrownBy(() -> service.createOrg(orgCreateReq(999L)))
            .isInstanceOf(SecurityException.class);
        verify(orgDomainService, never()).insert(any(SysOrg.class));
    }

    @Test
    @DisplayName("创建子组织：父节点游离（不属于任何树）→ 拒绝（九轮 P1）")
    void createOrgWithFloatingParentRejected() {
        SysOrg parent = new SysOrg();
        parent.setId(999L);
        parent.setOrgType("1");
        parent.setLevel(1);
        when(orgDomainService.selectValidById(TENANT, 999L)).thenReturn(parent);
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, 999L))
            .thenThrow(new BizException(11002, "组织树根无法解析"));

        assertThatThrownBy(() -> service.createOrg(orgCreateReq(999L)))
            .isInstanceOf(BizException.class);
        verify(orgDomainService, never()).insert(any(SysOrg.class));
    }

    @Test
    @DisplayName("创建子组织：只走父节点 UPDATE 门禁，不再额外要求类型级 CREATE（十轮 P1）")
    void createOrgChildSkipsTypeLevelCreate() {
        SysOrg parent = new SysOrg();
        parent.setId(999L);
        parent.setOrgType("1");
        parent.setLevel(1);
        when(orgDomainService.selectValidById(TENANT, 999L)).thenReturn(parent);
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, 999L)).thenReturn("1");
        when(orgDomainService.findByCode(TENANT, "NEW")).thenReturn(null);
        doAnswer(inv -> {
            SysOrg o = inv.getArgument(0);
            o.setId(888L);
            return null;
        }).when(orgDomainService).insert(any(SysOrg.class));
        when(localProjectionDomainService.upsertAdminOrg(anyLong(), anyLong(), anyString(), anyString(),
            anyLong(), any(), any(), any(), any())).thenReturn(700L);

        service.createOrg(orgCreateReq(999L));

        verify(permissionValidator, never()).checkTypeLevel(anyString(), anyString());
        verify(permissionValidator).checkInstanceLevel(eq(AdminResourceType.ORG), eq("999"), anyString());
    }

    @Test
    @DisplayName("创建顶级组织：走类型级 CREATE（十轮 P1 门禁分支）")
    void createOrgTopLevelUsesTypeLevelCreate() {
        when(orgDomainService.findByCode(TENANT, "NEW")).thenReturn(null);
        doAnswer(inv -> {
            SysOrg o = inv.getArgument(0);
            o.setId(888L);
            return null;
        }).when(orgDomainService).insert(any(SysOrg.class));
        when(localProjectionDomainService.upsertAdminOrg(anyLong(), anyLong(), anyString(), anyString(),
            any(), any(), any(), any(), any())).thenReturn(700L);

        service.createOrg(new cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq(1, "新组织", null, "NEW", 1, 1));

        verify(permissionValidator).checkTypeLevel(eq(AdminResourceType.ORG), anyString());
        verify(permissionValidator, never()).checkInstanceLevel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("岗位移动：迁移成员 relation 并登记受影响用户（十轮 P1）")
    void movePositionMigratesMemberRelation() {
        SysOrg org = org("A", "2", 2); // POSITION
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org);
        when(orgDomainService.getDescendantIds(TENANT, ORG_ID)).thenReturn(List.of());
        SysOrg newParent = new SysOrg();
        newParent.setId(20L);
        newParent.setOrgType("1");
        newParent.setLevel(4);
        when(orgDomainService.selectValidById(TENANT, 20L)).thenReturn(newParent);
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, ORG_ID)).thenReturn("1");
        when(orgTreeConfigDomainService.resolveTreeRootExternalId(TENANT, 20L)).thenReturn("1");
        when(localProjectionDomainService.migratePositionRelation(TENANT, ORG_ID, 1L, 20L))
            .thenReturn(Set.of(501L, 502L));

        service.updateOrg(new OrgUpdateReq(ORG_ID, null, 20L, null, null, null));

        verify(localProjectionDomainService).migratePositionRelation(TENANT, ORG_ID, 1L, 20L);
        verify(orgDomainService).update(any(SysOrg.class));
    }

    private static cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq orgCreateReq(Long parentId) {
        return new cn.ac.fage.accessmesh.access.admin.dto.req.OrgCreateReq(1, "新组织", parentId, "NEW", 1, 1);
    }

    @Test
    @DisplayName("部分更新：省略 code/name/status/sort 时保留原值，不 NPE")
    void partialUpdateKeepsUnchangedFields() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));

        service.updateOrg(new OrgUpdateReq(ORG_ID, null, null, null, null, null));

        ArgumentCaptor<SysOrg> captor = ArgumentCaptor.forClass(SysOrg.class);
        verify(orgDomainService).update(captor.capture());
        SysOrg updated = captor.getValue();
        assertThat(updated.getCode()).isEqualTo("A");      // 省略 code → 保留
        assertThat(updated.getName()).isEqualTo("旧名称");  // 省略 name → 保留
        assertThat(updated.getStatus()).isEqualTo(1);       // 省略 status → 保留
        assertThat(updated.getSortOrder()).isEqualTo(5);    // 省略 sort → 保留
        assertThat(updated.getParentId()).isEqualTo(1L);    // 省略 parentOrgId → 不移动
        assertThat(updated.getLevel()).isEqualTo(2);        // 不移动 → level 不变
        // 投影按更新后事实同步（名称保持旧值）；parentOrgType 由 resolveParentOrgType 解析（父查询 null → null）
        verify(localProjectionDomainService).upsertAdminOrg(
            eq(TENANT), eq(ORG_ID), eq("1"), eq("旧名称"), eq(1L), eq(null), eq(1), eq(5), any());
    }

    @Test
    @DisplayName("部分更新：提供 code/name/sort 时更新对应字段")
    void partialUpdateAppliesProvidedFields() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));
        when(orgDomainService.findByCode(TENANT, "B")).thenReturn(null);

        service.updateOrg(new OrgUpdateReq(ORG_ID, "新名称", null, "B", 0, 9));

        ArgumentCaptor<SysOrg> captor = ArgumentCaptor.forClass(SysOrg.class);
        verify(orgDomainService).update(captor.capture());
        SysOrg updated = captor.getValue();
        assertThat(updated.getName()).isEqualTo("新名称");
        assertThat(updated.getCode()).isEqualTo("B");
        assertThat(updated.getStatus()).isEqualTo(0);
        assertThat(updated.getSortOrder()).isEqualTo(9);
    }

    @Test
    @DisplayName("省略 code 时跳过重复校验（原实现 req.code().equals 直接 NPE）")
    void partialUpdateWithoutCodeSkipsDuplicateCheck() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));

        service.updateOrg(new OrgUpdateReq(ORG_ID, null, null, null, null, null));

        verify(orgDomainService, never()).findByCode(anyLong(), anyString());
    }
}
