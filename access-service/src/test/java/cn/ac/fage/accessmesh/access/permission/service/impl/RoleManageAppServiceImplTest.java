package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

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
            engine,
            treeWriteLockSupport
        );
    }

    @Test
    @DisplayName("树写锁无条件先于业务校验：updateRole/moveRole 异常路径同样验证入口已接锁")
    void treeWriteLockTakenBeforeRoleWriteValidation() {
        when(subjectDomainService.selectValidRoleById(1L, 99L)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateRole(1L, 99L, "n", null, null, null, null, 9L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
        org.mockito.Mockito.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);

        org.mockito.Mockito.clearInvocations(treeWriteLockSupport);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.moveRole(1L, 99L, null, 9L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
        org.mockito.Mockito.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);
    }

    @Test
    void shouldShortCircuitRoleTreeWhenDomainDoesNotCoverRoleType() {
        when(domainClassifyService.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", ResourceTypeCode.ROLE))
            .thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);

            assertEquals(List.of(), service.getRoleTree(1L, "OPS", false));
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

            assertThrows(SecurityException.class, () -> service.getRoleTree(1L, null, false));
        }
        verifyNoInteractions(abstractRoleMapper);
        verifyNoInteractions(domainClassifyService);
    }

    /** T-PERM-022：树返回全部有效角色（含禁用）——status 为展示字段，禁用角色可见可再启用。 */
    @Test
    void shouldLoadTreeWithDisabledRoles() {
        AbstractRole disabled = new AbstractRole();
        disabled.setId(103L);
        disabled.setTenantId(1L);
        disabled.setRoleType(6);
        disabled.setName("访客");
        disabled.setStatus(0);
        when(abstractRoleMapper.selectValidRoleTree(1L, false)).thenReturn(List.of(disabled));
        when(typeResolutionService.resolveTypeCode(1L, "role_type", 6)).thenReturn("BASIC_ROLE");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);

            List<cn.ac.fage.accessmesh.access.permission.dto.resp.RoleTreeResp> tree = service.getRoleTree(1L, null, false);
            assertEquals(1, tree.size());
            assertEquals(0, tree.get(0).root().status());
        }
        verify(abstractRoleMapper).selectValidRoleTree(1L, false);
    }

    /** T-PERM-022：enabledOnly=true 透传 SQL 过滤（授权页主体树，前端入参后端过滤）。 */
    @Test
    void shouldPassThroughEnabledOnlyToTreeQuery() {
        when(abstractRoleMapper.selectValidRoleTree(1L, true)).thenReturn(List.of());

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);

            assertEquals(List.of(), service.getRoleTree(1L, null, true));
        }
        verify(abstractRoleMapper).selectValidRoleTree(1L, true);
    }

    /** T-PERM-022：detail 用业务键二元组定位，禁用角色可查（再启用流程依赖）。 */
    @Test
    void shouldResolveDetailByBusinessKey() {
        when(typeResolutionService.resolveTypeValue(1L, "role_type", "BASIC_ROLE")).thenReturn(6);
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(6);
        role.setName("运维角色");
        role.setStatus(0);
        role.setExternalId("ext-1");
        when(abstractRoleMapper.selectByTypeAndExternalId(1L, 6, "ext-1")).thenReturn(role);
        when(typeResolutionService.resolveTypeCode(1L, "role_type", 6)).thenReturn("BASIC_ROLE");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);

            cn.ac.fage.accessmesh.access.permission.dto.resp.RoleResp resp = service.getRole(1L, "BASIC_ROLE", "ext-1");
            assertNotNull(resp);
            assertEquals(123L, resp.id());
            assertEquals("ext-1", resp.externalId());
            assertEquals(0, resp.status());
        }
    }

    /** T-PERM-022：未知 roleTypeCode 与 list 空分页同口径——不抛错返回 null，不触库。 */
    @Test
    void shouldReturnNullDetailForUnknownRoleType() {
        when(typeResolutionService.resolveTypeValue(1L, "role_type", "GHOST")).thenReturn(null);

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);

            assertNull(service.getRole(1L, "GHOST", "ext-1"));
        }
        verifyNoInteractions(abstractRoleMapper);
    }

    /** T-PERM-022 评审收口：detail/list/count 读接口补类型级 ROLE:VIEW 门禁（与 /tree 同款，
     * list 信息量 >= tree 不设门禁会使 tree 门禁事实可绕）。 */
    @Test
    void shouldRejectDetailAndListWithoutRoleViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getRole(1L, "BASIC_ROLE", "ext-1"));
            assertThrows(SecurityException.class, () -> service.listRoles(1L, null, null, null, null, 0, 10));
            assertThrows(SecurityException.class, () -> service.countRoles(1L, null, null, null, null));
        }
        verifyNoInteractions(abstractRoleMapper);
    }

    /** T-PERM-022 评审收口：删除有 BASIC 子级的 BASIC 角色级联软删子孙（悬挂子树防护）。
     * 旧实现级联根仅 GROUP_ROLE/ORG，本用例为回归锁。 */
    @Test
    void shouldCascadeBasicDescendantsOnDelete() {
        AbstractRole root = new AbstractRole();
        root.setId(123L);
        root.setTenantId(1L);
        root.setRoleType(cn.ac.fage.accessmesh.access.permission.enums.RoleType.BASIC_ROLE.getValue());
        root.setName("父角色");
        root.setStatus(1);
        root.setExternalId("root");
        when(subjectDomainService.selectValidRolesByIds(1L, java.util.Set.of(123L))).thenReturn(List.of(root));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq(java.util.Set.of("123")), eq(OperationCodeConstants.MANAGE))).thenReturn(java.util.Set.of());
        when(subjectDomainService.resolveDescendantRoleIdsBatch(1L, java.util.Set.of(123L)))
            .thenReturn(List.of(456L));
        when(subjectDomainService.findUserIdsByEffectiveRoles(1L, java.util.Set.of(123L, 456L)))
            .thenReturn(java.util.Set.of());
        when(typeResolutionService.resolveTypeCode(1L, "role_type",
            cn.ac.fage.accessmesh.access.permission.enums.RoleType.BASIC_ROLE.getValue())).thenReturn("BASIC_ROLE");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.deleteRoles(1L, List.of(123L), 100L);
        }

        verify(subjectDomainService).softDeleteRoleBatch(1L, java.util.Set.of(123L, 456L));
        verify(localProjectionDomainService).softDeleteRoleResources(1L, java.util.Set.of(123L, 456L));
        // 项目规则「父有权子有权」（设计定案）：仅根做一次 MANAGE 检查，子孙不做独立权限过滤
        // （旧实现对子孙集合二次检查，本断言为回归锁）
        verify(engine, org.mockito.Mockito.times(1)).getDeniedResourceCodes(
            eq(1L), eq(100L), eq(ResourceTypeCode.ROLE), any(), eq(OperationCodeConstants.MANAGE));
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

    /** T-PERM-043：createRole 显式拒绝 GROUP_ROLE（ROLE_TYPE_MISMATCH 20022），不触类型解析与投影。 */
    @Test
    void shouldRejectCreateGroupRoleWithTypeMismatch() {
        RoleCreateReq req = new RoleCreateReq(null, "GROUP_ROLE", "ext-group", "分组角色", null, null);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createRole(1L, req, 100L))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .extracting(ex -> ((cn.ac.fage.accessmesh.common.exception.BizException) ex).getErrorCode())
                .isEqualTo(cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode());
        }
        verifyNoInteractions(typeResolutionService);
        verifyNoInteractions(subjectDomainService);
        verifyNoInteractions(localProjectionDomainService);
    }

    /** T-PERM-043：updateRole 显式拒绝 GROUP_ROLE 目标（按现行类型判定，ROLE_TYPE_MISMATCH 20022）。 */
    @Test
    void shouldRejectUpdateGroupRoleWithTypeMismatch() {
        AbstractRole groupRole = new AbstractRole();
        groupRole.setId(123L);
        groupRole.setTenantId(1L);
        groupRole.setRoleType(5);
        groupRole.setName("分组角色");
        groupRole.setStatus(1);
        when(subjectDomainService.selectValidRoleById(1L, 123L)).thenReturn(groupRole);
        // 生产代码 GROUP_ROLE 判定先于 MANAGE 门禁（与 rejectIfLocalRole 同为权限前前置检查）；
        // lenient 使本用例对未来门禁前移也保持通过（此时 stub 才被消费）
        lenient().when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq("123"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.updateRole(1L, 123L, "新名", 0, null, null, null, 100L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .extracting(ex -> ((cn.ac.fage.accessmesh.common.exception.BizException) ex).getErrorCode())
            .isEqualTo(cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode());
        verifyNoInteractions(localProjectionDomainService);
        verifyNoInteractions(abstractRoleMapper);
    }

    /** T-FE-016：extraClear=true 强制清空 extra——update(entity) 默认忽略 null 列，须 UpdateEntity 显式写。 */
    @Test
    void shouldClearExtraWhenExtraClearTrue() {
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(6);
        role.setName("旧名");
        role.setStatus(1);
        role.setExtra("{\"k\":1}");
        when(subjectDomainService.selectValidRoleById(1L, 123L)).thenReturn(role);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq("123"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(typeResolutionService.resolveTypeCode(1L, "role_type", 6)).thenReturn("BASIC_ROLE");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.updateRole(1L, 123L, null, null, null, null, true, 100L);
        }

        // 强制写列：落库实体 extra 必须为 null（旧实现无该参数/普通 update(entity) 下 null 列被忽略）
        verify(abstractRoleMapper).update(argThat(e -> e.getId().equals(123L) && e.getExtra() == null));
    }

    /** T-FE-016：extraClear 缺省（null）且 extra 未传——extra 保留原值，不得误清。 */
    @Test
    void shouldKeepExtraWhenExtraClearAbsent() {
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(6);
        role.setName("旧名");
        role.setStatus(1);
        role.setExtra("{\"k\":1}");
        when(subjectDomainService.selectValidRoleById(1L, 123L)).thenReturn(role);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq("123"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(typeResolutionService.resolveTypeCode(1L, "role_type", 6)).thenReturn("BASIC_ROLE");

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.updateRole(1L, 123L, null, null, null, null, null, 100L);
        }

        verify(abstractRoleMapper).update(argThat(e -> e.getId().equals(123L) && "{\"k\":1}".equals(e.getExtra())));
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

            service.updateRole(1L, 123L, "新名", 0, null, null, null, 100L);
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
        // 角色事实 BATCH_DELETE + 投影 DELETE 各一次
        verify(auditDomainService, org.mockito.Mockito.times(2)).recordChangeLog(any(), any());
    }

    /** T-ACCESS-019：moveRole 投影镜像新父节点，旧父链成员在树变更前预计算失效。 */
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

    /** T-PERM-022：move 父子类型一致校验——跨类型嵌套拒绝 20022（旧实现无此校验，本用例为回归锁）。 */
    @Test
    void shouldRejectMoveAcrossRoleTypes() {
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(cn.ac.fage.accessmesh.access.permission.enums.RoleType.BASIC_ROLE.getValue());
        AbstractRole parent = new AbstractRole();
        parent.setId(200L);
        parent.setTenantId(1L);
        parent.setRoleType(cn.ac.fage.accessmesh.access.permission.enums.RoleType.GROUP_ROLE.getValue());
        when(subjectDomainService.selectValidRoleById(1L, 123L)).thenReturn(role);
        when(subjectDomainService.selectValidRoleById(1L, 200L)).thenReturn(parent);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq("123"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.moveRole(1L, 123L, 200L, 100L));
        assertEquals(20022, ex.getErrorCode());
        verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any());
    }

    /** T-PERM-022：move 环路防护——移动到自身拒绝 20050。 */
    @Test
    void shouldRejectMoveToSelf() {
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(cn.ac.fage.accessmesh.access.permission.enums.RoleType.BASIC_ROLE.getValue());
        when(subjectDomainService.selectValidRoleById(1L, 123L)).thenReturn(role);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq("123"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.moveRole(1L, 123L, 123L, 100L));
        assertEquals(20050, ex.getErrorCode());
        verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any());
    }

    /** T-PERM-022：move 环路防护——目标父为子孙节点拒绝 20050（parent 链成环后递归 CTE 不收敛）。 */
    @Test
    void shouldRejectMoveToDescendant() {
        AbstractRole role = new AbstractRole();
        role.setId(123L);
        role.setTenantId(1L);
        role.setRoleType(cn.ac.fage.accessmesh.access.permission.enums.RoleType.BASIC_ROLE.getValue());
        AbstractRole parent = new AbstractRole();
        parent.setId(300L);
        parent.setTenantId(1L);
        parent.setRoleType(cn.ac.fage.accessmesh.access.permission.enums.RoleType.BASIC_ROLE.getValue());
        when(subjectDomainService.selectValidRoleById(1L, 123L)).thenReturn(role);
        when(subjectDomainService.selectValidRoleById(1L, 300L)).thenReturn(parent);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
            eq("123"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(subjectDomainService.resolveDescendantRoleIdsBatch(1L, java.util.Set.of(123L)))
            .thenReturn(List.of(300L));

        BizException ex = assertThrows(BizException.class, () -> service.moveRole(1L, 123L, 300L, 100L));
        assertEquals(20050, ex.getErrorCode());
        verify(abstractRoleMapper, org.mockito.Mockito.never()).update(any());
    }
}
