package cn.ac.fage.accessmesh.access.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AccessRequestContext} 单元测试（T-ACCESS-004）。
 * <p>覆盖：bind/get/clear、四要素便捷 getter、快照恢复、TenantContextHolder 兼容门面
 * （无上下文 → 仅租户作用域；有上下文 → 替换租户保留身份）。</p>
 */
class AccessRequestContextTest {

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    @Test
    @DisplayName("bind 用户上下文后可读取四要素")
    void shouldBindAndReadUserContext() {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));

        assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
        assertThat(AccessRequestContext.getServiceCode()).isNull();
    }

    @Test
    @DisplayName("bind 服务上下文后 serviceCode 可读")
    void shouldBindAndReadServiceContext() {
        AccessRequestContext.bind(RequestContext.service(2L, "example-service"));

        assertThat(AccessRequestContext.getTenantId()).isEqualTo(2L);
        assertThat(AccessRequestContext.getOperatorId()).isNull();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.SERVICE);
        assertThat(AccessRequestContext.getServiceCode()).isEqualTo("example-service");
    }

    @Test
    @DisplayName("clear 清空全部上下文")
    void shouldClearContext() {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        AccessRequestContext.clear();

        assertThat(AccessRequestContext.get()).isNull();
        assertThat(AccessRequestContext.getTenantId()).isNull();
        assertThat(AccessRequestContext.getOperatorId()).isNull();
        assertThat(AccessRequestContext.getCallerType()).isNull();
    }

    @Test
    @DisplayName("snapshot/restore 支持异步/嵌套任务显式传递")
    void shouldSnapshotAndRestore() {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
        RequestContext snapshot = AccessRequestContext.snapshot();

        AccessRequestContext.bind(RequestContext.task(2L));
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(2L);
        assertThat(AccessRequestContext.getOperatorId()).isNull();

        AccessRequestContext.restore(snapshot);
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
    }

    @Test
    @DisplayName("兼容门面：无上下文时 setTenantId 建立仅租户作用域")
    void facadeShouldCreateTenantScopeWhenNoContext() {
        TenantContextHolder.setTenantId(42L);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(42L);
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(42L);
        // 仅租户作用域：无操作者、TASK 语义
        assertThat(AccessRequestContext.getOperatorId()).isNull();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.TASK);
    }

    @Test
    @DisplayName("兼容门面：已有上下文时 setTenantId 仅替换租户、保留身份")
    void facadeShouldReplaceTenantKeepIdentity() {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));

        TenantContextHolder.setTenantId(2L);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(2L);
        assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
    }

    @Test
    @DisplayName("兼容门面：clear 委托新上下文")
    void facadeClearShouldDelegate() {
        TenantContextHolder.setTenantId(42L);
        TenantContextHolder.clear();

        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("兼容门面：已有上下文时 setTenantId(null) → 租户置空但身份保留（评审 P2-4 语义锁定）")
    void facadeShouldNullTenantKeepIdentity() {
        AccessRequestContext.bind(RequestContext.user(1L, 100L));

        TenantContextHolder.setTenantId(null);

        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
        // 租户过滤语义：MyBatis-Flex TenantFactory 对 null 返回空数组（不过滤）
        assertThat(AccessRequestContext.getTenantId()).isNull();
    }
}
