package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OperatorContext} 单元测试（T-ACCESS-004 重构：只读可信上下文）。
 * <p>覆盖：USER 上下文返回操作者；SERVICE / 匿名 / 未绑定上下文拒绝（fail-closed，
 * 满足"内部凭证不能隐式获得 /api/perm/** 全权限"验收）。</p>
 */
class OperatorContextTest {

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    @Test
    @DisplayName("USER 上下文 → 返回绑定操作者")
    void shouldReturnOperatorId_whenUserContext() {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));

        assertThat(OperatorContext.getOperatorId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("SERVICE 上下文（纯凭证调用）→ 拒绝（内部凭证不隐式获得操作者身份）")
    void shouldReject_whenServiceContext() {
        AccessRequestContext.bind(RequestContext.service(1L, "example-service"));

        assertThatThrownBy(OperatorContext::getOperatorId)
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("匿名上下文 → 拒绝")
    void shouldReject_whenAnonymousContext() {
        AccessRequestContext.bind(RequestContext.anonymous());

        assertThatThrownBy(OperatorContext::getOperatorId)
            .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("未绑定上下文 → 拒绝")
    void shouldReject_whenNoContext() {
        assertThatThrownBy(OperatorContext::getOperatorId)
            .isInstanceOf(SecurityException.class);
    }
}
