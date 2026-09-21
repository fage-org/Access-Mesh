package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.resource.entity.ResourceDependency;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 编译图只读查询门禁与响应回归。 */
@ExtendWith(MockitoExtension.class)
class DependencyAppServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 100L;
    private static final Long SOURCE_ID = 11L;
    private static final Long TARGET_ID = 22L;

    @Mock private ResourceDependencyMapper dependencyMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;
    @Mock private cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantInsightDomainService autoGrantInsightDomainService;
    @Mock private cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService operationPermissionDomainService;
    @Mock private cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService conditionDomainService;
    @Mock private cn.ac.fage.accessmesh.access.resource.service.domain.PermissionManifestNormalizer manifestNormalizer;
    @Mock private cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompilationDomainService compilation;
    @Mock private cn.ac.fage.accessmesh.access.resource.mapper.ServiceManifestSyncMapper manifestSyncMapper;

    private DependencyAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DependencyAppServiceImpl(dependencyMapper, resourceEntityMapper,
            typeResolutionService, engine, autoGrantInsightDomainService, operationPermissionDomainService,
            conditionDomainService, manifestNormalizer, compilation, manifestSyncMapper);
    }

    // ========== 辅助 ==========

    private void stubTypeLevelPermission(String operationCode, boolean allowed) {
        when(engine.hasPermissionByCode(eq(TENANT), anyLong(), eq(ResourceTypeCode.DEPENDENCY),
            isNull(), eq(operationCode))).thenReturn(allowed);
    }

    private static ResourceDependency newDep(Long id, Long sourceId, Long targetId, Long sourceBits, Long requiredBits) {
        ResourceDependency dep = new ResourceDependency();
        dep.setId(id);
        dep.setTenantId(TENANT);
        dep.setResourceEntityId(sourceId);
        dep.setDependsOnResourceEntityId(targetId);
        dep.setSourceOperationBits(sourceBits);
        dep.setRequiredOperationBits(requiredBits);
        dep.setDeleteFlag(0L);
        return dep;
    }

    private static ResourceEntity newResource(Long id, String code, String name, Integer typeValue) {
        ResourceEntity entity = new ResourceEntity();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setCode(code);
        entity.setName(name);
        entity.setResourceType(typeValue);
        return entity;
    }

    // ========== 读门禁（T-PERM-031：原 list/graph/check 三端点无门禁） ==========

    @Nested
    class ReadEndpointsViewGate {

        @Test
        void shouldRejectListWithoutViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
                stubTypeLevelPermission(OperationCode.VIEW, false);

                assertThrows(SecurityException.class, () -> service.listDependencies(TENANT, null));
                verify(dependencyMapper, never()).selectByTenantAndResourceEntityId(anyLong(), any());
            }
        }

        @Test
        void shouldRejectGraphFullListWithoutViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
                stubTypeLevelPermission(OperationCode.VIEW, false);

                assertThrows(SecurityException.class, () -> service.listAllDependencies(TENANT));
                verify(dependencyMapper, never()).selectByTenantId(anyLong());
            }
        }

        @Test
        void shouldRejectCycleCheckWithoutViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
                stubTypeLevelPermission(OperationCode.VIEW, false);

                assertThrows(SecurityException.class, () -> service.hasDependencyCycle(TENANT,
                    new cn.ac.fage.accessmesh.access.resource.dto.req.ResourceDependencyCheckReq(
                        "MENU", "menu:sys", null, "API", "api:hello", null)));
                verify(typeResolutionService, never()).resolveResourceId(anyLong(), anyString(), anyString(), any(), any());
            }
        }

        @Test
        void shouldBackfillTypeCodeAndNameInListResp() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
                stubTypeLevelPermission(OperationCode.VIEW, true);
                ResourceDependency dep = newDep(7L, SOURCE_ID, TARGET_ID, 2L, 4L);
                dep.setMaintainSource("MANIFEST");
                dep.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                dep.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
                when(dependencyMapper.selectByTenantId(TENANT)).thenReturn(List.of(dep));
                when(resourceEntityMapper.selectValidByIds(eq(TENANT), anySet())).thenReturn(List.of(
                    newResource(SOURCE_ID, "menu:sys", "系统菜单", 0),
                    newResource(TARGET_ID, "api:hello", "鉴权接口", 2)));
                when(typeResolutionService.batchResolveTypeCodes(eq(TENANT), eq("resource_type"), anySet()))
                    .thenReturn(Map.of(0, "MENU", 2, "API"));

                List<cn.ac.fage.accessmesh.access.resource.dto.resp.ResourceDependencyResp> resps =
                    service.listAllDependencies(TENANT);

                assertThat(resps).hasSize(1);
                var resp = resps.get(0);
                assertThat(resp.sourceResourceTypeCode()).isEqualTo("MENU");
                assertThat(resp.targetResourceTypeCode()).isEqualTo("API");
                assertThat(resp.sourceResourceName()).isEqualTo("系统菜单");
                assertThat(resp.targetResourceName()).isEqualTo("鉴权接口");
                assertThat(resp.maintainSource()).isEqualTo("MANIFEST");
                assertThat(resp.updatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 0, 0));
            }
        }
    }

}
