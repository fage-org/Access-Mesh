package cn.ac.fage.accessmesh.permission.config;

import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex租户配置类
 * <p>
 * 通过TenantFactory配置MyBatis-Flex的租户支持。
 * 所有SQL查询将自动追加tenant_id = ?条件，
 * 适用于包含tenant_id列的表。
 * </p>
 *
 * <p>租户ID从TenantContextHolder获取，确保数据隔离。
 * </p>
 */
@Configuration
public class MybatisFlexTenantConfig {

    /**
     * 初始化租户工厂
     * <p>
     * 设置TenantFactory，从TenantContextHolder获取当前租户ID。
     * MyBatis-Flex将自动为查询添加租户过滤条件。
     * </p>
     */
    @PostConstruct
    public void init() {
        TenantManager.setTenantFactory(() -> {
            Long tenantId = TenantContextHolder.getTenantId();
            return tenantId != null ? new Object[]{tenantId} : new Object[0];
        });
    }
}