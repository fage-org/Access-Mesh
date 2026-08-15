package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.cache.AdminCacheCatalog;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgVisibilityServiceImplTest {

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private CacheService cacheService;

    private OrgVisibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgVisibilityServiceImpl(
            typeResolutionService, engine, orgTreeConfigDomainService, orgDomainService, cacheService);
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
        @DisplayName("engine 允许的 orgId 被保留（一次批量 getDeniedIds）")
        void allowedOrgs_kept() {
            when(typeResolutionService.resolveUserId(1L, LocalProjectionOwner.SUBJECT_ADMIN_USER, "100"))
                .thenReturn(1000L);
            when(engine.getDeniedIds(eq(1L), eq(1000L), eq(AdminResourceType.ORG),
                anySet(), eq("VIEW")))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(300L)));

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
        @DisplayName("缓存命中时不再查权限引擎")
        void cacheHit_skipsEngine() {
            when(cacheService.get(eq(AdminCacheCatalog.ORG_VISIBILITY), eq(1L), eq(100L)))
                .thenReturn(Set.of(100L, 200L));

            Set<Long> result = service.getOperatorVisibleDefaultTreeOrgIds(1L, 100L);

            assertThat(result).containsExactlyInAnyOrder(100L, 200L);
            verifyNoInteractions(engine);
        }

        @Test
        @DisplayName("无默认树配置时返回空集")
        void noDefaultConfigs_returnsEmpty() {
            when(cacheService.get(any(), any(), any())).thenReturn(null);
            when(orgTreeConfigDomainService.findDefaultConfigs(1L)).thenReturn(List.of());

            Set<Long> result = service.getOperatorVisibleDefaultTreeOrgIds(1L, 100L);

            assertThat(result).isEmpty();
        }
    }
}
