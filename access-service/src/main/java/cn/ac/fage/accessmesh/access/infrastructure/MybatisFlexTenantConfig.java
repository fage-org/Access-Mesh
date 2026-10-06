package cn.ac.fage.accessmesh.access.infrastructure;

import com.mybatisflex.core.tenant.TenantManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 生成 SQL 的租户配置。
 * <p>
 * global-config.tenant-column 识别实体租户列；生成的查询/更新 SQL 追加可信租户条件。
 * 手写 XML 仍自行显式约束 tenant_id，不受此工厂改写。
 * </p>
 */
@Configuration
public class MybatisFlexTenantConfig {

    @PostConstruct
    public void init() {
        TenantManager.setTenantFactory(() -> {
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId == null) {
                throw new IllegalStateException("缺少租户上下文，禁止生成租户 SQL");
            }
            return new Object[]{tenantId};
        });
    }
}
