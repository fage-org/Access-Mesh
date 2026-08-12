package cn.ac.fage.accessmesh.access.infrastructure;

import com.mybatisflex.core.tenant.TenantManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 租户配置（admin 与 permission 域共用）。
 * <p>
 * 通过 TenantFactory 配置 MyBatis-Flex 的租户支持，自动为所有 SQL 查询追加 tenant_id 条件。
 * </p>
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
