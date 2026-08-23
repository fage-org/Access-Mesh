package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-ACCESS-005 评审 P2 修复 + T-PERM-042 业务编码语义：
 * checkBatchInstanceLevel 走 engine.getDeniedResourceCodes 批量查询
 * （一次操作者解析 + 引擎内部 code→entity 批量解析 + 一次角色解析），不再循环单条 engine.query。
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
    @DisplayName("批量校验：一次 getDeniedResourceCodes（业务编码语义），不循环单条 query")
    void batchCheckUsesSingleBatchQuery() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(501L);
        when(engine.getDeniedResourceCodes(eq(TENANT), eq(501L), eq("ORG"),
            eq(new LinkedHashSet<>(List.of("1", "2", "3"))), eq("VIEW")))
            .thenReturn(Set.of());

        assertThatCode(() -> validator.checkBatchInstanceLevel("ORG", List.of("1", "2", "3"), "VIEW"))
            .doesNotThrowAnyException();

        verify(engine).getDeniedResourceCodes(eq(TENANT), eq(501L), eq("ORG"), any(), eq("VIEW"));
        verify(engine, never()).query(any());
    }

    @Test
    @DisplayName("批量校验：denied 业务编码直接抛 SecurityException")
    void batchCheckRejectsDeniedCodes() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(501L);
        when(engine.getDeniedResourceCodes(eq(TENANT), eq(501L), eq("ORG"), any(), eq("VIEW")))
            .thenReturn(new LinkedHashSet<>(List.of("2")));

        assertThatThrownBy(() -> validator.checkBatchInstanceLevel("ORG", List.of("1", "2"), "VIEW"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("[2]");
    }

    @Test
    @DisplayName("批量校验：未解析业务编码由引擎归入拒绝集合 → fail-closed 拒绝")
    void batchCheckUnresolvedCodeRejected() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(501L);
        // 引擎对无投影实体的 code fail-closed（含未解析与无权限两类），门面统一拒绝
        when(engine.getDeniedResourceCodes(eq(TENANT), eq(501L), eq("ORG"), any(), eq("VIEW")))
            .thenReturn(new LinkedHashSet<>(List.of("2")));

        assertThatThrownBy(() -> validator.checkBatchInstanceLevel("ORG", List.of("1", "2"), "VIEW"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("[2]");
        verify(engine).getDeniedResourceCodes(eq(TENANT), eq(501L), eq("ORG"), any(), eq("VIEW"));
    }

    @Test
    @DisplayName("批量校验：空列表直接返回，不查询")
    void batchCheckEmptySkipsQuery() {
        validator.checkBatchInstanceLevel("ORG", List.of(), "VIEW");
        verify(typeResolutionService, never()).resolveUserId(any(), any(), any());
        verify(engine, never()).getDeniedResourceCodes(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("批量校验：操作者主体不存在 → SecurityException fail-closed")
    void batchCheckMissingOperatorRejected() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(null);

        assertThatThrownBy(() -> validator.checkBatchInstanceLevel("ORG", List.of("1"), "VIEW"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("操作者主体不存在");
    }

    // ===== 单点门禁（T-PERM-042 评审 P2：checkAndThrow 收敛到 engine.hasPermissionByCode）=====

    @Test
    @DisplayName("单点校验：checkInstanceLevel 走 hasPermissionByCode（业务编码语义），通过时不抛")
    void instanceCheckShouldUseHasPermissionByCode() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(501L);
        when(engine.hasPermissionByCode(TENANT, 501L, "ORG", "1", "VIEW")).thenReturn(true);

        assertThatCode(() -> validator.checkInstanceLevel("ORG", "1", "VIEW"))
            .doesNotThrowAnyException();
        verify(engine).hasPermissionByCode(TENANT, 501L, "ORG", "1", "VIEW");
        verify(engine, never()).query(any());
    }

    @Test
    @DisplayName("单点校验：拒绝时抛 SecurityException")
    void instanceCheckShouldThrowWhenDenied() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(501L);
        when(engine.hasPermissionByCode(TENANT, 501L, "ORG", "1", "VIEW")).thenReturn(false);

        assertThatThrownBy(() -> validator.checkInstanceLevel("ORG", "1", "VIEW"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("权限被拒绝");
    }

    @Test
    @DisplayName("类型级校验：checkTypeLevel 以 null code 走 hasPermissionByCode")
    void typeLevelCheckShouldPassNullCode() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(501L);
        when(engine.hasPermissionByCode(TENANT, 501L, "USER", null, "CREATE")).thenReturn(true);

        assertThatCode(() -> validator.checkTypeLevel("USER", "CREATE"))
            .doesNotThrowAnyException();
        verify(engine).hasPermissionByCode(TENANT, 501L, "USER", null, "CREATE");
    }

    @Test
    @DisplayName("单点校验：操作者主体不存在 → SecurityException fail-closed")
    void instanceCheckMissingOperatorRejected() {
        when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(null);

        assertThatThrownBy(() -> validator.checkInstanceLevel("ORG", "1", "VIEW"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("操作者主体不存在");
        verify(engine, never()).hasPermissionByCode(any(), any(), any(), any(), any());
    }
}
