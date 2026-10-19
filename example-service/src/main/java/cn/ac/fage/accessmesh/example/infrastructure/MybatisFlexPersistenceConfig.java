package cn.ac.fage.accessmesh.example.infrastructure;

import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.tenant.TenantManager;
import com.mybatisflex.spring.boot.ConfigurationCustomizer;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.InitializingBean;
import javax.sql.DataSource;
import java.time.LocalDateTime;

/** Explicit tenant predicates in example Mappers; generated SQL has no implicit all-tenant fallback. */
@Configuration
public class MybatisFlexPersistenceConfig implements ConfigurationCustomizer {
    @Bean
    InitializingBean exampleSchemaReady(DataSource dataSource) {
        return () -> {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("SELECT id, tenant_id, active_policy_version FROM demo_dataset WHERE false");
            }
        };
    }

    @PostConstruct
    void requireExplicitTenantQueries() {
        TenantManager.setTenantFactory(() -> {
            throw new IllegalStateException("example Mapper must explicitly bind a trusted tenantId");
        });
    }
    @Override
    public void customize(FlexConfiguration configuration) {
        configuration.getTypeHandlerRegistry().register(LocalDateTime.class,
            new TimestamptzLocalDateTimeTypeHandler());
    }
}
