package cn.ac.fage.accessmesh.access.infrastructure;

import com.mybatisflex.core.tenant.TenantManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MybatisFlexTenantConfigTest {
    @Test
    void missingContextFailsAndExplicitBootstrapBypassHasBoundedScope() {
        var originalFactory = TenantManager.getTenantFactory();
        var originalContext = AccessRequestContext.snapshot();
        try {
            new MybatisFlexTenantConfig().init();
            AccessRequestContext.clear();
            assertThatThrownBy(() -> TenantManager.getTenantIds("sys_user"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("租户上下文");
            TenantManager.withoutTenantCondition(() ->
                assertThat(TenantManager.getTenantIds("sys_user")).isNull());
            assertThatThrownBy(() -> TenantManager.getTenantIds("sys_user"))
                .isInstanceOf(IllegalStateException.class);
            AccessRequestContext.bind(RequestContext.task(17L));
            assertThat(TenantManager.getTenantIds("sys_user")).containsExactly(17L);
        } finally {
            TenantManager.setTenantFactory(originalFactory);
            AccessRequestContext.restore(originalContext);
        }
    }
}
