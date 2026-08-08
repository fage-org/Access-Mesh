package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionGrantPlanDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGrantPlanDomainServiceImplTest {

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private PermissionGrantDomainService permissionGrantDomainService;
    @Mock private RoleResourcePermissionMapper rolePermissionMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private PermissionConditionMapper permissionConditionMapper;
    @Mock private DomainConfigMapper domainConfigMapper;

    private PermissionGrantPlanDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantPlanDomainServiceImpl(
            typeResolutionService, domainClassifyService, permissionGrantDomainService,
            rolePermissionMapper, resourceEntityMapper, operationPermissionMapper,
            permissionConditionMapper, domainConfigMapper, new ObjectMapper());
    }

    @Test
    void shouldPrepareNestedChildWithExplicitWildcardSubPermConfig() {
        stubNestedCreateBase();
        stubDelegationAllowed();
        DomainConfig subPerm = new DomainConfig();
        subPerm.setBizDomainId(7L);
        subPerm.setConfigType("SUB_PERM");
        subPerm.setExtra("*");
        when(domainClassifyService.findDomainIdsByTypeCodes(1L, java.util.Set.of("DATA")))
            .thenReturn(Map.of("DATA", 7L));
        when(domainConfigMapper.selectByTenantId(1L)).thenReturn(List.of(subPerm));

        PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
            1L, 10L, 20L, null, nestedCreatePlan());

        assertEquals(1, prepared.creates().size());
        assertEquals(1, prepared.creates().get(0).children().size());
        assertEquals(2, prepared.delegationKeys().size());
    }

    @Test
    void shouldFailClosedWhenSubPermConfigIsMissing() {
        stubNestedCreateBase();
        when(domainClassifyService.findDomainIdsByTypeCodes(1L, java.util.Set.of("DATA")))
            .thenReturn(Map.of("DATA", 7L));
        when(domainConfigMapper.selectByTenantId(1L)).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class, () ->
            service.prevalidate(1L, 10L, 20L, null, nestedCreatePlan()));

        assertEquals(20011, exception.getErrorCode());
    }

    @Test
    void shouldRejectRemoveWhenAffectedRowCountChanges() {
        PermissionGrantPlanDomainService.PreparedGrantPlan prepared =
            new PermissionGrantPlanDomainService.PreparedGrantPlan(
                1L, 20L, List.of(), List.of(), List.of(99L), java.util.Set.of());
        when(rolePermissionMapper.softDeleteBatch(eq(1L), eq(List.of(99L)), any()))
            .thenReturn(0);

        BizException exception = assertThrows(BizException.class, () -> service.apply(prepared));

        assertEquals(20036, exception.getErrorCode());
    }

    private void stubNestedCreateBase() {
        when(rolePermissionMapper.selectValidByRoleId(1L, 20L)).thenReturn(List.of());
        when(typeResolutionService.batchResolveTypeValues(
            1L, "resource_type", java.util.Set.of("DATA")))
            .thenReturn(Map.of("DATA", 4));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any()))
            .thenReturn(Map.of(
                new ResourceResolveKey("DATA", "report:sales", "default", null), 101L,
                new ResourceResolveKey("DATA", "city:shanghai", "default", null), 102L));
        OperationPermission globalView = new OperationPermission();
        globalView.setId(9L);
        globalView.setResourceType(null);
        globalView.setCode("VIEW");
        globalView.setBinaryBit(2L);
        globalView.setInheritMask(0L);
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, null))
            .thenReturn(List.of(globalView));
    }

    private void stubDelegationAllowed() {
        when(permissionGrantDomainService.checkCanGrant(eq(1L), eq(10L), any(), eq(null)))
            .thenAnswer(invocation -> {
                java.util.Set<PermissionGrantDomainService.GrantCheckKey> keys = invocation.getArgument(2);
                return keys.stream().collect(java.util.stream.Collectors.toMap(
                    key -> String.format("%s:%s:%s:%s:%s",
                        key.resourceTypeCode(), key.resourceCode(), key.codeType(),
                        key.operationCode(), key.scopeAll() ? "ALL" : "SPECIFIC"),
                    key -> new PermissionGrantDomainService.GrantCheckResult(true, null)));
            });
    }

    private ApplyGrantPlanReq.GrantPlan nestedCreatePlan() {
        ApplyGrantPlanReq.GrantRecordKey parent = new ApplyGrantPlanReq.GrantRecordKey(
            "DATA", "report:sales", "default", "VIEW", ScopeMode.INSTANCE, null, false);
        ApplyGrantPlanReq.GrantRecordKey child = new ApplyGrantPlanReq.GrantRecordKey(
            "DATA", "city:shanghai", "default", "VIEW", ScopeMode.INSTANCE, null, false);
        return new ApplyGrantPlanReq.GrantPlan(
            List.of(new ApplyGrantPlanReq.CreateItem(parent, null, List.of(child))),
            List.of(), List.of());
    }
}
