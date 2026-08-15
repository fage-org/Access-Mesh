package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.OrgUpdateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private AuditDomainService auditDomainService;

    private OrgWriteAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgWriteAppServiceImpl(
            orgDomainService,
            userOrgDomainService,
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
    @DisplayName("移动成功：更新自身 level + 子树 level 增量同步")
    void moveUpdatesLevelAndSubtree() {
        when(orgDomainService.selectValidById(TENANT, ORG_ID)).thenReturn(org("A", "1", 2));
        when(orgDomainService.getDescendantIds(TENANT, ORG_ID)).thenReturn(List.of(30L));
        SysOrg newParent = new SysOrg();
        newParent.setId(20L);
        newParent.setOrgType("1");
        newParent.setLevel(4);
        when(orgDomainService.selectValidById(TENANT, 20L)).thenReturn(newParent);

        service.updateOrg(new OrgUpdateReq(ORG_ID, null, 20L, null, null, null));

        ArgumentCaptor<SysOrg> captor = ArgumentCaptor.forClass(SysOrg.class);
        verify(orgDomainService).update(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(5); // 新父级 level 4 + 1
        assertThat(captor.getValue().getParentId()).isEqualTo(20L);
        verify(orgDomainService).batchUpdateLevel(TENANT, List.of(30L), 3); // 5 - 2
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
        // 投影按更新后事实同步（名称保持旧值）
        verify(localProjectionDomainService).upsertAdminOrg(
            eq(TENANT), eq(ORG_ID), eq("1"), eq("旧名称"), eq(1L), eq(1), eq(5), any());
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
