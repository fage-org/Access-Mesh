package cn.ac.fage.accessmesh.access.admin.service.security;

import cn.ac.fage.accessmesh.access.admin.cache.AdminCacheCatalog;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OrgVisibilityService 单元测试。
 * <p>
 * 验证 batchCheckAuth 结果正确映射为可见 orgId 集合，
 * 以及缓存回填逻辑。
 */
@ExtendWith(MockitoExtension.class)
class OrgVisibilityServiceImplTest {

    @Mock private PermissionFeignClient permissionFeignClient;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private CacheService cacheService;

    private OrgVisibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrgVisibilityServiceImpl(
            permissionFeignClient, orgTreeConfigDomainService, orgDomainService, cacheService);
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
        @DisplayName("batchCheckAuth allowed 的 orgId 被保留")
        void allowedOrgs_kept() {
            BatchAuthCheckResp resp = new BatchAuthCheckResp(List.of(
                item("ADMIN_ORG", "100", "VIEW", true),
                item("ADMIN_ORG", "200", "VIEW", true),
                item("ADMIN_ORG", "300", "VIEW", false)
            ));
            PermResult<BatchAuthCheckResp> permResult = PermResult.success(resp);
            when(permissionFeignClient.batchCheckAuth(any())).thenReturn(permResult);

            Set<Long> result = service.filterVisibleOrgIds(1L, 100L, List.of(100L, 200L, 300L));

            assertThat(result).containsExactlyInAnyOrder(100L, 200L);
            assertThat(result).doesNotContain(300L);
        }

        @Test
        @DisplayName("batchCheckAuth 失败时返回空集（降级不阻断）")
        void feignFailure_returnsEmpty() {
            when(permissionFeignClient.batchCheckAuth(any())).thenThrow(new RuntimeException("timeout"));

            Set<Long> result = service.filterVisibleOrgIds(1L, 100L, List.of(100L));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getOperatorVisibleDefaultTreeOrgIds")
    class GetOperatorVisibleDefaultTreeOrgIds {

        @Test
        @DisplayName("缓存命中时不再调 Feign")
        void cacheHit_skipsFeign() {
            when(cacheService.get(eq(AdminCacheCatalog.ORG_VISIBILITY), eq(1L), eq(100L)))
                .thenReturn(Set.of(100L, 200L));

            Set<Long> result = service.getOperatorVisibleDefaultTreeOrgIds(1L, 100L);

            assertThat(result).containsExactlyInAnyOrder(100L, 200L);
            verifyNoInteractions(permissionFeignClient);
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

    private static BatchAuthCheckResp.AuthCheckItemResult item(
        String resourceTypeCode, String resourceCode, String operationCode, boolean allowed) {
        return new BatchAuthCheckResp.AuthCheckItemResult(
            resourceTypeCode, resourceCode, operationCode, allowed, null, null, null);
    }
}
