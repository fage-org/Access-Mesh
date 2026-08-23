package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManageAppServiceImplTest {

    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private PermQueryEngine engine;

    private RoleManageAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleManageAppServiceImpl(
            abstractRoleMapper,
            subjectDomainService,
            typeResolutionService,
            domainClassifyService,
            new ObjectMapper(),
            auditDomainService,
            new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(),
            localProjectionDomainService,
            engine
        );
    }

    @Test
    void shouldShortCircuitRoleTreeWhenDomainDoesNotCoverRoleType() {
        when(domainClassifyService.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", ResourceTypeCode.ROLE))
            .thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);

            assertEquals(List.of(), service.getRoleTree(1L, "OPS"));
        }
        verifyNoInteractions(abstractRoleMapper);
    }

    /** T-PERM-042（architecture §14.5）：角色树读接口补类型级 ROLE:VIEW 门禁。 */
    @Test
    void shouldRejectRoleTreeWithoutRoleViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getRoleTree(1L, null));
        }
        verifyNoInteractions(abstractRoleMapper);
        verifyNoInteractions(domainClassifyService);
    }

    /** T-ACCESS-019：createRole 同事务维护 resource_entity(ROLE) 投影（code=roleId）并登记变更日志。 */
    @Test
    void shouldProjectRoleResourceOnCreate() {
        RoleCreateReq req = new RoleCreateReq(null, "BASIC_ROLE", "ext-1", "运维角色", null, null);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(typeResolutionService.resolveTypeValue(1L, "role_type", "BASIC_ROLE")).thenReturn(6);
        when(subjectDomainService.createRole(eq(1L), isNull(), eq(6), eq("ext-1"), eq("运维角色"), isNull(), isNull()))
            .thenReturn(123L);
        AbstractRole created = new AbstractRole();
        created.setId(123L);
        created.setTenantId(1L);
        created.setRoleType(6);
        created.setName("运维角色");
        created.setStatus(1);
        when(abstractRoleMapper.selectOneById(123L)).thenReturn(created);
        when(typeResolutionService.resolveTypeCode(1L, "role_type", 6)).thenReturn("BASIC_ROLE");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.createRole(1L, req, 100L);
        }

        verify(localProjectionDomainService).upsertRoleResource(1L, 123L, "运维角色", 1, null);
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    /** T-ACCESS-019：updateRole 同事务镜像 name/status 到 ROLE 投影。 */
    @Test
    void shouldProjectRoleResourceOnUpdate() {
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(6);
        role.setName("旧名");
        role.setStatus(1);
        when(subjectDomainService.selectValidRoleById(1L, 123L)).thenReturn(role);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq("123"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(typeResolutionService.resolveTypeCode(1L, "role_type", 6)).thenReturn("BASIC_ROLE");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.updateRole(1L, 123L, "新名", 0, null, null, 100L);
        }

        verify(localProjectionDomainService).upsertRoleResource(1L, 123L, "新名", 0, null);
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    /** T-ACCESS-019：deleteRoles 同事务批量软删 ROLE 投影（含级联子孙角色）并按预计算用户失效。 */
    @Test
    void shouldSoftDeleteRoleResourcesOnRemove() {
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(6);
        role.setName("运维角色");
        when(subjectDomainService.selectValidRolesByIds(eq(1L), eq(java.util.Set.of(123L)))).thenReturn(List.of(role));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq(java.util.Set.of("123")), eq(OperationCodeConstants.MANAGE))).thenReturn(java.util.Set.of());
        when(subjectDomainService.findUserIdsByEffectiveRoles(1L, java.util.Set.of(123L)))
            .thenReturn(java.util.Set.of(55L));
        when(typeResolutionService.resolveTypeCode(1L, "role_type", 6)).thenReturn("BASIC_ROLE");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.deleteRoles(1L, List.of(123L), 100L);
        }

        verify(localProjectionDomainService).softDeleteRoleResources(1L, java.util.Set.of(123L));
        // 角色事实 BATCH_DELETE + 投影 DELETE 各一次（评审 P1-4）
        verify(auditDomainService, org.mockito.Mockito.times(2)).recordChangeLog(any(), any());
    }

    /** T-ACCESS-019：moveRole 投影镜像新父节点，旧父链成员在树变更前预计算失效（评审 P1-3）。 */
    @Test
    void shouldProjectRoleResourceOnMove() {
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(6);
        role.setName("运维角色");
        role.setStatus(1);
        AbstractRole newParent = new AbstractRole();
        newParent.setId(200L);
        newParent.setTenantId(1L);
        newParent.setRoleType(6);
        when(subjectDomainService.selectValidRoleById(1L, 123L)).thenReturn(role);
        when(subjectDomainService.selectValidRoleById(1L, 200L)).thenReturn(newParent);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq("123"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(subjectDomainService.findUserIdsByEffectiveRoles(1L, java.util.Set.of(123L)))
            .thenReturn(java.util.Set.of(66L));

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.moveRole(1L, 123L, 200L, 100L);
        }

        verify(localProjectionDomainService).upsertRoleResource(1L, 123L, "运维角色", 1, 200L);
        verify(auditDomainService).recordChangeLog(any(), any());
    }
}
