package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-ACCESS-005 评审 P2 修复：checkBatchInstanceLevel 走 engine.getDeniedIds 批量查询
 * （一次操作者解析 + 一次角色解析），不再循环单条 engine.query 的 N 次放大。
 */
@ExtendWith(MockitoExtension.class)
class AdminPermissionValidatorImplTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private AdminPermissionValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new AdminPermissionValidatorImpl(typeResolutionService, engine);
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("批量校验：批量解析业务键 → 投影 ID + 一次 getDeniedIds，不循环单条 query")
    void batchCheckUsesSingleBatchQuery() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_ADMIN_USER, "9"))
            .thenReturn(501L);
        // 九轮评审 P1：getDeniedIds 按 resource_entity.id 查询，先解析业务键
        when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList())).thenReturn(
            Map.of(
                new ResourceResolveKey("ADMIN_ORG", "1", null, null), 1001L,
                new ResourceResolveKey("ADMIN_ORG", "2", null, null), 1002L,
                new ResourceResolveKey("ADMIN_ORG", "3", null, null), 1003L));
        when(engine.getDeniedIds(eq(TENANT), eq(501L), eq("ADMIN_ORG"),
            eq(new LinkedHashSet<>(List.of(1001L, 1002L, 1003L))), eq("VIEW")))
            .thenReturn(Set.of());

        assertThatCode(() -> validator.checkBatchInstanceLevel("ADMIN_ORG", List.of("1", "2", "3"), "VIEW"))
            .doesNotThrowAnyException();

        verify(engine).getDeniedIds(eq(TENANT), eq(501L), eq("ADMIN_ORG"), any(), eq("VIEW"));
        verify(engine, never()).query(any());
    }

    @Test
    @DisplayName("批量校验：denied 投影 ID 映射回业务键后抛 SecurityException")
    void batchCheckRejectsDeniedIds() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_ADMIN_USER, "9"))
            .thenReturn(501L);
        when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList())).thenReturn(
            Map.of(
                new ResourceResolveKey("ADMIN_ORG", "1", null, null), 1001L,
                new ResourceResolveKey("ADMIN_ORG", "2", null, null), 1002L));
        when(engine.getDeniedIds(eq(TENANT), eq(501L), eq("ADMIN_ORG"), any(), eq("VIEW")))
            .thenReturn(new LinkedHashSet<>(List.of(1002L))); // 业务键 "2" 被拒绝

        assertThatThrownBy(() -> validator.checkBatchInstanceLevel("ADMIN_ORG", List.of("1", "2"), "VIEW"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("[2]");
    }

    @Test
    @DisplayName("批量校验：业务键无投影实体 → fail-closed 拒绝")
    void batchCheckMissingProjectionRejected() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_ADMIN_USER, "9"))
            .thenReturn(501L);
        when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
            .thenReturn(Map.of(new ResourceResolveKey("ADMIN_ORG", "1", null, null), 1001L));

        assertThatThrownBy(() -> validator.checkBatchInstanceLevel("ADMIN_ORG", List.of("1", "2"), "VIEW"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("资源投影不存在");
        verify(engine, never()).getDeniedIds(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("批量校验：空列表直接返回，不查询")
    void batchCheckEmptySkipsQuery() {
        validator.checkBatchInstanceLevel("ADMIN_ORG", List.of(), "VIEW");
        verify(typeResolutionService, never()).resolveUserId(any(), any(), any());
        verify(engine, never()).getDeniedIds(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("批量校验：操作者主体不存在 → SecurityException fail-closed")
    void batchCheckMissingOperatorRejected() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_ADMIN_USER, "9"))
            .thenReturn(null);

        assertThatThrownBy(() -> validator.checkBatchInstanceLevel("ADMIN_ORG", List.of("1"), "VIEW"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("操作者主体不存在");
    }
}
