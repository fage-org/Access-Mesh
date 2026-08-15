package cn.ac.fage.accessmesh.access.application.query;

import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.application.query.impl.OrgVisibilityQueryServiceImpl;
import cn.ac.fage.accessmesh.access.application.query.mapper.OrgVisibilityQueryMapper;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 组织可见性查询服务（跨域只读）行为测试。
 */
@ExtendWith(MockitoExtension.class)
class OrgVisibilityQueryServiceImplTest {

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;
    @Mock private OrgVisibilityQueryMapper orgVisibilityQueryMapper;
    @Mock private CacheService cacheService;

    private OrgVisibilityQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgVisibilityQueryServiceImpl(
            typeResolutionService, engine, orgVisibilityQueryMapper, cacheService);
    }

    @Nested
    @DisplayName("filterVisibleOrgIds")
    class FilterVisibleOrgIds {

        @Test
        @DisplayName("空集合返回空结果")
        void emptyInput_returnsEmpty() {
            Set<Long> result = service.filterVisibleOrgIds(1L, 100L, List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("engine 允许的 orgId 被保留（批量解析业务键 + 一次 getDeniedIds）")
        void allowedOrgs_kept() {
            when(typeResolutionService.resolveUserId(1L, LocalProjectionOwner.SUBJECT_ADMIN_USER, "100"))
                .thenReturn(1000L);
            when(typeResolutionService.batchResolveResourceIds(eq(1L), anyList())).thenReturn(
                Map.of(
                    new ResourceResolveKey(AdminResourceType.ORG, "100", null, null), 1001L,
                    new ResourceResolveKey(AdminResourceType.ORG, "200", null, null), 1002L,
                    new ResourceResolveKey(AdminResourceType.ORG, "300", null, null), 1003L));
            when(engine.getDeniedIds(eq(1L), eq(1000L), eq(AdminResourceType.ORG),
                anySet(), eq("VIEW")))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(1003L))); // 300 → denied

            Set<Long> result = service.filterVisibleOrgIds(1L, 100L, List.of(100L, 200L, 300L));

            assertThat(result).containsExactlyInAnyOrder(100L, 200L);
            assertThat(result).doesNotContain(300L);
            verify(engine).getDeniedIds(eq(1L), eq(1000L), eq(AdminResourceType.ORG),
                anySet(), eq("VIEW"));
        }

        @Test
        @DisplayName("批量引擎异常整体传播（fail-closed，不再单条静默跳过）")
        void engineFailure_propagates() {
            when(typeResolutionService.resolveUserId(1L, LocalProjectionOwner.SUBJECT_ADMIN_USER, "100"))
                .thenReturn(1000L);
            when(typeResolutionService.batchResolveResourceIds(eq(1L), anyList())).thenReturn(
                Map.of(new ResourceResolveKey(AdminResourceType.ORG, "100", null, null), 1001L));
            when(engine.getDeniedIds(eq(1L), eq(1000L), eq(AdminResourceType.ORG),
                anySet(), eq("VIEW")))
                .thenThrow(new RuntimeException("timeout"));

            assertThatThrownBy(() -> service.filterVisibleOrgIds(1L, 100L, List.of(100L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("timeout");
        }
    }

    @Nested
    @DisplayName("getOperatorVisibleDefaultTreeOrgIds")
    class GetOperatorVisibleDefaultTreeOrgIds {

        @Test
        @DisplayName("缓存命中时不再查权限引擎与树查询")
        void cacheHit_skipsEngine() {
            when(cacheService.get(eq(PermCacheCatalog.ORG_VISIBILITY), eq(1L), eq(100L)))
                .thenReturn(Set.of(100L, 200L));

            Set<Long> result = service.getOperatorVisibleDefaultTreeOrgIds(1L, 100L);

            assertThat(result).containsExactlyInAnyOrder(100L, 200L);
            verifyNoInteractions(engine);
            verifyNoInteractions(orgVisibilityQueryMapper);
        }

        @Test
        @DisplayName("无默认树配置时返回空集")
        void noDefaultConfigs_returnsEmpty() {
            when(cacheService.get(any(), any(), any())).thenReturn(null);
            when(orgVisibilityQueryMapper.selectDefaultTreeRootOrgIds(1L)).thenReturn(List.of());

            Set<Long> result = service.getOperatorVisibleDefaultTreeOrgIds(1L, 100L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("默认树子树批量查询后按引擎过滤并回填缓存")
        void descendantFiltered_thenCached() {
            when(cacheService.get(any(), any(), any())).thenReturn(null);
            when(orgVisibilityQueryMapper.selectDefaultTreeRootOrgIds(1L)).thenReturn(List.of(50L));
            when(orgVisibilityQueryMapper.selectDescendantOrgIds(1L, 50L)).thenReturn(List.of(50L, 60L));
            when(typeResolutionService.resolveUserId(1L, LocalProjectionOwner.SUBJECT_ADMIN_USER, "100"))
                .thenReturn(1000L);
            when(typeResolutionService.batchResolveResourceIds(eq(1L), anyList())).thenReturn(
                Map.of(
                    new ResourceResolveKey(AdminResourceType.ORG, "50", null, null), 5001L,
                    new ResourceResolveKey(AdminResourceType.ORG, "60", null, null), 6001L));
            when(engine.getDeniedIds(eq(1L), eq(1000L), eq(AdminResourceType.ORG),
                anySet(), eq("VIEW")))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(6001L))); // 60 → denied

            Set<Long> result = service.getOperatorVisibleDefaultTreeOrgIds(1L, 100L);

            assertThat(result).containsExactly(50L);
            verify(cacheService).put(eq(PermCacheCatalog.ORG_VISIBILITY), eq(1L), eq(100L), eq(Set.of(50L)));
        }
    }
}
