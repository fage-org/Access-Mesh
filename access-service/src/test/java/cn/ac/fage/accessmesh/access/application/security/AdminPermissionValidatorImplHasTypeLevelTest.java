package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T-ADMIN-021：hasTypeLevel 非抛出判定语义锁定。
 * <p>
 * 三条不变量：①引擎成功响应时返回值透传（false 仅限明确拒绝）；
 * ②引擎技术故障包装 SystemException(99999) 向上（fail-closed，不得静默降级为 false
 * ——否则调用方会返回裁剪后的树，P2-1 故障验收矛盾）；③操作者主体缺失同样归技术故障
 * （hasTypeLevel 的 false 语义仅限引擎成功响应且 allowed=false）。
 * </p>
 */
class AdminPermissionValidatorImplHasTypeLevelTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 42L;
    private static final Long SUBJECT_ID = 52L;

    private final TypeResolutionService typeResolutionService = mock(TypeResolutionService.class);
    private final PermQueryEngine engine = mock(PermQueryEngine.class);
    private final AdminPermissionValidatorImpl validator =
        new AdminPermissionValidatorImpl(typeResolutionService, engine);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    private void subjectResolves() {
        when(typeResolutionService.resolveUserId(
            eq(TENANT), eq(LocalProjectionOwner.SUBJECT_LOCAL_USER), eq(String.valueOf(OPERATOR))))
            .thenReturn(SUBJECT_ID);
    }

    @Test
    @DisplayName("引擎成功响应：返回值透传（true/false 仅限明确判定）")
    void engineResultPassesThrough() {
        subjectResolves();
        when(engine.hasPermissionByCode(TENANT, SUBJECT_ID, "ORG", null, "VIEW_POSITION"))
            .thenReturn(true)
            .thenReturn(false);

        assertThat(validator.hasTypeLevel("ORG", "VIEW_POSITION")).isTrue();
        assertThat(validator.hasTypeLevel("ORG", "VIEW_POSITION")).isFalse();
    }

    @Test
    @DisplayName("引擎技术故障：包装 SystemException(99999)，不静默降级为 false")
    void engineFailureWrapsSystemException() {
        subjectResolves();
        when(engine.hasPermissionByCode(TENANT, SUBJECT_ID, "ORG", null, "VIEW_POSITION"))
            .thenThrow(new RuntimeException("simulated database failure"));

        assertThatThrownBy(() -> validator.hasTypeLevel("ORG", "VIEW_POSITION"))
            .isInstanceOf(SystemException.class)
            .satisfies(e -> assertThat(((SystemException) e).getErrorCode())
                .isEqualTo(GlobalErrorCode.SYSTEM_ERROR.code()))
            .hasMessageContaining("simulated database failure");
    }

    @Test
    @DisplayName("操作者主体缺失：归技术故障 SystemException(99999)，不返回 false")
    void missingSubjectIsTechnicalFailure() {
        when(typeResolutionService.resolveUserId(anyLong(), eq(LocalProjectionOwner.SUBJECT_LOCAL_USER), eq("42")))
            .thenReturn(null);

        assertThatThrownBy(() -> validator.hasTypeLevel("ORG", "VIEW_POSITION"))
            .isInstanceOf(SystemException.class)
            .satisfies(e -> assertThat(((SystemException) e).getErrorCode())
                .isEqualTo(GlobalErrorCode.SYSTEM_ERROR.code()))
            .hasMessageContaining("操作者主体不存在");
    }
}
