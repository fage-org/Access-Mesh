package cn.ac.fage.accessmesh.admin.config;

import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Configures MyBatis-Flex tenant support via TenantFactory.
 * All SQL queries will automatically append tenant_id = ? condition
 * for tables that have a tenant_id column.
 */
@Configuration
public class MybatisFlexTenantConfig {

    @PostConstruct
    public void init() {
        TenantManager.setTenantFactory(() -> {
            Long tenantId = TenantContextHolder.getTenantId();
            return tenantId != null ? new Object[]{tenantId} : new Object[0];
        });
    }
}
