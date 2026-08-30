package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.SubPermAllowedTypesReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.SubPermAllowedTypesResp;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantPlanDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-PERM-034 AppService 层收口用例：sub-perm-allowed-types 门禁与策略映射（§6.5.2——
 * 角色定位失败 20001、无 ROLE:VIEW 抛 SecurityException、策略结果直接序列化）、
 * apply-grant-plan 变更日志 §6.8 聚合形状（eventType/items[].permission 6 字段业务键/role 摘要）。
 */
@ExtendWith(MockitoExtension.class)
class PermissionGrantAppServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 10L;
    private static final Long ROLE_ID = 20L;

    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private PermissionConditionMapper permissionConditionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private PermissionGrantPlanDomainService permissionGrantPlanDomainService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private PermissionGrantAppService service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantAppServiceImpl(
            abstractRoleMapper, resourceEntityMapper, operationPermissionMapper,
            permissionConditionMapper, rolePermMapper, permissionGrantPlanDomainService,
            auditDomainService, typeResolutionService, engine, new ObjectMapper());
    }

    private SubPermAllowedTypesReq subPermReq() {
        return new SubPermAllowedTypesReq(null, "BASIC_ROLE", "role_report_editor", "MENU");
    }

    // ========== sub-perm-allowed-types（§6.5.2） ==========

    @Test
    void shouldRejectSubPermQueryWhenRoleMissing() {
        when(typeResolutionService.resolveRoleId(TENANT, "BASIC_ROLE", "role_report_editor", null))
            .thenReturn(null);

        cn.ac.fage.accessmesh.common.exception.BizException exception = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.subPermAllowedTypes(TENANT, subPermReq()));

        assertEquals(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    @Test
    void shouldRejectSubPermQueryWithoutRoleView() {
        when(typeResolutionService.resolveRoleId(TENANT, "BASIC_ROLE", "role_report_editor", null))
            .thenReturn(ROLE_ID);

        try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
            opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
            when(engine.hasPermissionByCode(TENANT, OPERATOR, ResourceTypeCode.ROLE,
                String.valueOf(ROLE_ID), OperationCodeConstants.VIEW)).thenReturn(false);

            // 无 VIEW 抛 SecurityException（不采用空结果掩盖鉴权失败，区别于 list）
            assertThrows(SecurityException.class, () -> service.subPermAllowedTypes(TENANT, subPermReq()));
        }
    }

    @Test
    void shouldSerializePolicyResultDirectly() {
        when(typeResolutionService.resolveRoleId(TENANT, "BASIC_ROLE", "role_report_editor", null))
            .thenReturn(ROLE_ID);
        when(permissionGrantPlanDomainService.resolveSubPermissionPolicy(TENANT, "MENU"))
            .thenReturn(new PermissionGrantPlanDomainService.SubPermissionPolicy(
                PermissionGrantPlanDomainService.SubPermissionPolicy.Mode.ALLOW_LIST,
                null, List.of("BUTTON", "DATA")));

        try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
            opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
            when(engine.hasPermissionByCode(TENANT, OPERATOR, ResourceTypeCode.ROLE,
                String.valueOf(ROLE_ID), OperationCodeConstants.VIEW)).thenReturn(true);

            SubPermAllowedTypesResp resp = service.subPermAllowedTypes(TENANT, subPermReq());

            assertEquals("MENU", resp.parentResourceTypeCode());
            assertEquals("ALLOW_LIST", resp.mode());
            assertEquals(List.of("BUTTON", "DATA"), resp.allowedChildResourceTypeCodes());
        }
    }

    @Test
    void shouldExposeReasonForAllowNone() {
        when(typeResolutionService.resolveRoleId(TENANT, "BASIC_ROLE", "role_report_editor", null))
            .thenReturn(ROLE_ID);
        when(permissionGrantPlanDomainService.resolveSubPermissionPolicy(TENANT, "MENU"))
            .thenReturn(new PermissionGrantPlanDomainService.SubPermissionPolicy(
                PermissionGrantPlanDomainService.SubPermissionPolicy.Mode.ALLOW_NONE,
                "PARENT_NOT_CONFIGURED", List.of()));

        try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
            opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
            when(engine.hasPermissionByCode(TENANT, OPERATOR, ResourceTypeCode.ROLE,
                String.valueOf(ROLE_ID), OperationCodeConstants.VIEW)).thenReturn(true);

            SubPermAllowedTypesResp resp = service.subPermAllowedTypes(TENANT, subPermReq());

            assertEquals("ALLOW_NONE", resp.mode());
            assertEquals("PARENT_NOT_CONFIGURED", resp.reason());
            assertEquals(List.of(), resp.allowedChildResourceTypeCodes());
        }
    }

    // ========== apply-grant-plan 变更日志 §6.8 聚合形状 ==========

    @Test
    void shouldWriteAggregateDiffSnapshot() throws Exception {
        when(typeResolutionService.resolveRoleId(TENANT, "BASIC_ROLE", "role_editor", "example"))
            .thenReturn(ROLE_ID);
        AbstractRole role = new AbstractRole();
        role.setId(ROLE_ID);
        role.setTenantId(TENANT);
        role.setName("报表编辑员");
        role.setStatus(PermissionConstants.ENABLED_STATUS);
        when(abstractRoleMapper.selectValidById(ROLE_ID, TENANT)).thenReturn(role);
        PermissionGrantPlanDomainService.PreparedGrantPlan prepared =
            new PermissionGrantPlanDomainService.PreparedGrantPlan(
                TENANT, ROLE_ID, List.of(), List.of(), List.of(5L), java.util.Set.of(),
                List.of(new PermissionGrantPlanDomainService.AuditPermissionKey(
                    "REMOVE", "REPORT", "report:sales", "default", "VIEW", ScopeMode.INSTANCE),
                    new PermissionGrantPlanDomainService.AuditPermissionKey(
                        "ADD", "REPORT", "report:hr", "default", "DATA_EDIT", ScopeMode.ALL)));
        when(permissionGrantPlanDomainService.prevalidate(eq(TENANT), eq(OPERATOR), eq(ROLE_ID),
            eq("example"), any(ApplyGrantPlanReq.GrantPlan.class))).thenReturn(prepared);
        when(rolePermMapper.selectValidByRoleId(TENANT, ROLE_ID)).thenReturn(List.of());

        ApplyGrantPlanReq req = new ApplyGrantPlanReq("example", "BASIC_ROLE", "role_editor",
            new ApplyGrantPlanReq.GrantPlan(List.of(), List.of(), List.of(5L)));

        try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
            opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
            when(engine.hasPermissionByCode(TENANT, OPERATOR, ResourceTypeCode.ROLE,
                String.valueOf(ROLE_ID), OperationCodeConstants.MANAGE)).thenReturn(true);

            service.applyGrantPlan(TENANT, req);
        }

        // 捕获 diff_snapshot 并断言 §6.8 聚合形状（一条聚合日志）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditDomainService.ChangeLogEntry>> captor =
            ArgumentCaptor.forClass(List.class);
        verify(auditDomainService).recordChangeLog(any(), captor.capture());
        assertEquals(1, captor.getValue().size());
        JsonNode snapshot = new ObjectMapper().readTree(captor.getValue().get(0).diffSnapshot());
        assertEquals("ROLE_PERMISSION_CHANGE", snapshot.path("eventType").asText());
        assertEquals(2, snapshot.path("items").size());
        JsonNode first = snapshot.path("items").get(0);
        assertEquals("REMOVE", first.path("changeType").asText());
        assertEquals("example", first.path("permission").path("domainCode").asText());
        assertEquals("REPORT", first.path("permission").path("resourceTypeCode").asText());
        assertEquals("report:sales", first.path("permission").path("resourceCode").asText());
        assertEquals("default", first.path("permission").path("codeType").asText());
        assertEquals("VIEW", first.path("permission").path("operationCode").asText());
        assertEquals("INSTANCE", first.path("permission").path("scopeMode").asText());
        assertEquals("role_editor", first.path("role").path("roleExternalId").asText());
        assertEquals("报表编辑员", first.path("role").path("roleName").asText());
        assertEquals("ADD", snapshot.path("items").get(1).path("changeType").asText());
        assertEquals("ALL", snapshot.path("items").get(1).path("permission").path("scopeMode").asText());
    }
}
