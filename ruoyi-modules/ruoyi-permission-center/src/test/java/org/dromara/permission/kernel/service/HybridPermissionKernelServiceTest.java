package org.dromara.permission.kernel.service;

import org.dromara.authcenter.api.enums.SubjectType;
import org.dromara.authcenter.api.enums.DataScopeType;
import org.dromara.authcenter.api.model.DataScopeDescriptor;
import org.dromara.authcenter.api.model.InterfaceDecisionResult;
import org.dromara.authcenter.api.model.InterfacePermissionRule;
import org.dromara.authcenter.api.model.InterfacePermissionSnapshot;
import org.dromara.authcenter.api.model.PermissionVersionInfo;
import org.dromara.authcenter.api.model.PrincipalContext;
import org.dromara.authcenter.api.request.DataScopeQueryRequest;
import org.dromara.authcenter.api.request.InterfacePermissionDecisionRequest;
import org.dromara.authcenter.api.request.InterfacePermissionSnapshotRequest;
import org.dromara.authcenter.api.request.PermissionVersionQueryRequest;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.kernel.service.impl.DatabaseDataScopeQueryService;
import org.dromara.permission.kernel.service.impl.DatabaseInterfacePermissionRuleQueryService;
import org.dromara.permission.kernel.service.impl.HybridPermissionKernelService;
import org.dromara.permission.kernel.service.impl.InMemoryPermissionKernelService;
import org.dromara.permission.service.PermissionVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class HybridPermissionKernelServiceTest {

    @Mock
    private PermissionVersionService permissionVersionService;

    @Mock
    private DatabaseInterfacePermissionRuleQueryService interfaceRuleQueryService;

    @Mock
    private DatabaseDataScopeQueryService dataScopeQueryService;

    private HybridPermissionKernelService service;

    @BeforeEach
    void setUp() {
        service = new HybridPermissionKernelService(
            new InMemoryPermissionKernelService(),
            permissionVersionService,
            interfaceRuleQueryService,
            dataScopeQueryService,
            new InterfaceSnapshotCache()
        );
    }

    @Test
    void queryVersion_usesPersistentPermissionVersion() {
        PcPermissionVersion current = new PcPermissionVersion();
        current.setTenantId(1L);
        current.setVersionNo(7L);
        current.setUpdatedAt(LocalDateTime.of(2026, 3, 25, 10, 30, 0));
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(current);

        PermissionVersionQueryRequest request = new PermissionVersionQueryRequest();
        request.setTenantId("1");
        request.setSubjectId("10001");
        request.setSubjectType(SubjectType.USER);

        PermissionVersionInfo info = service.queryVersion(request);

        assertEquals("1", info.getTenantId());
        assertEquals("USER:10001", info.getSubjectKey());
        assertEquals("1-v7", info.getPermissionVersion());
        assertEquals(current.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), info.getUpdatedAtEpochMilli());
    }

    @Test
    void queryInterfaceSnapshot_replacesInMemoryVersionWithPersistentVersion() {
        PcPermissionVersion current = new PcPermissionVersion();
        current.setTenantId(1L);
        current.setVersionNo(9L);
        current.setUpdatedAt(LocalDateTime.now());
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(current);
        when(interfaceRuleQueryService.listRules(1L, 10001L)).thenReturn(List.of());

        PrincipalContext principalContext = new PrincipalContext();
        principalContext.setTenantId("1");
        principalContext.setSubjectId("10001");
        principalContext.setSubjectType(SubjectType.USER);

        InterfacePermissionSnapshotRequest request = new InterfacePermissionSnapshotRequest();
        request.setPrincipalContext(principalContext);

        InterfacePermissionSnapshot snapshot = service.queryInterfaceSnapshot(request);

        assertEquals("1-v9", snapshot.getPermissionVersion());
    }

    @Test
    void queryInterfaceSnapshot_usesPersistentInterfaceRules() {
        PcPermissionVersion current = new PcPermissionVersion();
        current.setTenantId(1L);
        current.setVersionNo(12L);
        current.setUpdatedAt(LocalDateTime.of(2026, 3, 25, 20, 10, 0));
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(current);

        InterfacePermissionRule rule = new InterfacePermissionRule();
        rule.setCapabilityCode("ORDER_API:VIEW");
        rule.setServiceCode("order-service");
        rule.setHttpMethod("GET");
        rule.setPathPattern("/api/orders/**");
        when(interfaceRuleQueryService.listRules(1L, 10001L)).thenReturn(List.of(rule));

        PrincipalContext principalContext = new PrincipalContext();
        principalContext.setTenantId("1");
        principalContext.setSubjectId("10001");
        principalContext.setSubjectType(SubjectType.USER);

        InterfacePermissionSnapshotRequest request = new InterfacePermissionSnapshotRequest();
        request.setPrincipalContext(principalContext);

        InterfacePermissionSnapshot snapshot = service.queryInterfaceSnapshot(request);

        assertEquals("1", snapshot.getTenantId());
        assertEquals("USER:10001", snapshot.getSubjectKey());
        assertEquals("1-v12", snapshot.getPermissionVersion());
        assertEquals(1, snapshot.getRules().size());
        assertEquals("ORDER_API:VIEW", snapshot.getRules().get(0).getCapabilityCode());
        assertEquals("order-service", snapshot.getRules().get(0).getServiceCode());
    }

    @Test
    void decideInterface_matchesPersistentSnapshotRule() {
        PcPermissionVersion current = new PcPermissionVersion();
        current.setTenantId(1L);
        current.setVersionNo(15L);
        current.setUpdatedAt(LocalDateTime.now());
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(current);

        InterfacePermissionRule rule = new InterfacePermissionRule();
        rule.setCapabilityCode("ORDER_API:VIEW");
        rule.setServiceCode("order-service");
        rule.setHttpMethod("GET");
        rule.setPathPattern("/api/orders/**");
        when(interfaceRuleQueryService.listRules(1L, 10001L)).thenReturn(List.of(rule));

        PrincipalContext principalContext = new PrincipalContext();
        principalContext.setTenantId("1");
        principalContext.setSubjectId("10001");
        principalContext.setSubjectType(SubjectType.USER);

        InterfacePermissionDecisionRequest request = new InterfacePermissionDecisionRequest();
        request.setPrincipalContext(principalContext);
        request.setServiceCode("order-service");
        request.setRequestMethod("GET");
        request.setRequestPath("/api/orders/123");

        InterfaceDecisionResult result = service.decideInterface(request);

        assertTrue(result.isAllowed());
        assertEquals("ORDER_API:VIEW", result.getMatchedCapabilityCode());
        assertEquals("1-v15", result.getPermissionVersion());
        assertEquals("matched-interface-rule", result.getReason());
    }

    @Test
    void decideInterface_returnsDenyWhenPersistentSnapshotMisses() {
        PcPermissionVersion current = new PcPermissionVersion();
        current.setTenantId(1L);
        current.setVersionNo(16L);
        current.setUpdatedAt(LocalDateTime.now());
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(current);
        when(interfaceRuleQueryService.listRules(1L, 10001L)).thenReturn(List.of());

        PrincipalContext principalContext = new PrincipalContext();
        principalContext.setTenantId("1");
        principalContext.setSubjectId("10001");
        principalContext.setSubjectType(SubjectType.USER);

        InterfacePermissionDecisionRequest request = new InterfacePermissionDecisionRequest();
        request.setPrincipalContext(principalContext);
        request.setServiceCode("order-service");
        request.setRequestMethod("POST");
        request.setRequestPath("/api/orders");

        InterfaceDecisionResult result = service.decideInterface(request);

        assertFalse(result.isAllowed());
        assertEquals("1-v16", result.getPermissionVersion());
        assertEquals("no-interface-rule", result.getReason());
    }

    @Test
    void queryDataScopes_usesPersistentGrantedCapability() {
        DataScopeDescriptor descriptor = new DataScopeDescriptor();
        descriptor.setCapabilityCode("ORDER_DATA:VIEW");
        descriptor.setScopeType(DataScopeType.SELF);
        descriptor.setSubjectIds(List.of("10001"));
        when(dataScopeQueryService.queryDataScopes(1L, 10001L, "ORDER_DATA:VIEW")).thenReturn(List.of(descriptor));

        PrincipalContext principalContext = new PrincipalContext();
        principalContext.setTenantId("1");
        principalContext.setSubjectId("10001");
        principalContext.setSubjectType(SubjectType.USER);

        List<DataScopeDescriptor> result = service.queryDataScopes(new DataScopeQueryRequest(principalContext, "ORDER_DATA:VIEW"));

        assertEquals(1, result.size());
        assertEquals(DataScopeType.SELF, result.get(0).getScopeType());
        assertEquals(List.of("10001"), result.get(0).getSubjectIds());
    }

    @Test
    void queryInterfaceSnapshot_usesCacheOnSecondCall() {
        PcPermissionVersion current = new PcPermissionVersion();
        current.setTenantId(1L);
        current.setVersionNo(20L);
        current.setUpdatedAt(LocalDateTime.now());
        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(current);

        InterfacePermissionRule rule = new InterfacePermissionRule();
        rule.setCapabilityCode("ORDER_API:VIEW");
        rule.setServiceCode("order-service");
        rule.setHttpMethod("GET");
        rule.setPathPattern("/api/orders/**");
        when(interfaceRuleQueryService.listRules(1L, 10001L)).thenReturn(List.of(rule));

        PrincipalContext principalContext = new PrincipalContext();
        principalContext.setTenantId("1");
        principalContext.setSubjectId("10001");
        principalContext.setSubjectType(SubjectType.USER);

        InterfacePermissionSnapshotRequest request = new InterfacePermissionSnapshotRequest();
        request.setPrincipalContext(principalContext);

        // 第一次调用，缓存未命中
        InterfacePermissionSnapshot first = service.queryInterfaceSnapshot(request);
        assertEquals("1-v20", first.getPermissionVersion());
        assertEquals(1, first.getRules().size());

        // 第二次调用，缓存命中
        InterfacePermissionSnapshot second = service.queryInterfaceSnapshot(request);
        assertEquals("1-v20", second.getPermissionVersion());
        assertEquals(1, second.getRules().size());

        // 规则查询只调用一次（第二次走缓存）
        verify(interfaceRuleQueryService, times(1)).listRules(1L, 10001L);
    }

    @Test
    void queryInterfaceSnapshot_cacheMissOnVersionChange() {
        PcPermissionVersion version1 = new PcPermissionVersion();
        version1.setTenantId(1L);
        version1.setVersionNo(30L);
        version1.setUpdatedAt(LocalDateTime.now());

        PcPermissionVersion version2 = new PcPermissionVersion();
        version2.setTenantId(1L);
        version2.setVersionNo(31L);
        version2.setUpdatedAt(LocalDateTime.now());

        when(permissionVersionService.queryCurrentVersion(1L)).thenReturn(version1, version2);

        InterfacePermissionRule rule1 = new InterfacePermissionRule();
        rule1.setCapabilityCode("ORDER_API:VIEW");
        rule1.setServiceCode("order-service");
        rule1.setHttpMethod("GET");
        rule1.setPathPattern("/api/orders/**");

        InterfacePermissionRule rule2 = new InterfacePermissionRule();
        rule2.setCapabilityCode("ORDER_API:EDIT");
        rule2.setServiceCode("order-service");
        rule2.setHttpMethod("POST");
        rule2.setPathPattern("/api/orders");

        when(interfaceRuleQueryService.listRules(1L, 10001L)).thenReturn(List.of(rule1), List.of(rule2));

        PrincipalContext principalContext = new PrincipalContext();
        principalContext.setTenantId("1");
        principalContext.setSubjectId("10001");
        principalContext.setSubjectType(SubjectType.USER);

        InterfacePermissionSnapshotRequest request = new InterfacePermissionSnapshotRequest();
        request.setPrincipalContext(principalContext);

        // 第一次调用，版本 30
        InterfacePermissionSnapshot first = service.queryInterfaceSnapshot(request);
        assertEquals("1-v30", first.getPermissionVersion());
        assertEquals(1, first.getRules().size());
        assertEquals("ORDER_API:VIEW", first.getRules().get(0).getCapabilityCode());

        // 第二次调用，版本变化到 31，缓存失效
        InterfacePermissionSnapshot second = service.queryInterfaceSnapshot(request);
        assertEquals("1-v31", second.getPermissionVersion());
        assertEquals(1, second.getRules().size());
        assertEquals("ORDER_API:EDIT", second.getRules().get(0).getCapabilityCode());

        // 规则查询调用两次（版本变化导致缓存失效）
        verify(interfaceRuleQueryService, times(2)).listRules(1L, 10001L);
    }
}
