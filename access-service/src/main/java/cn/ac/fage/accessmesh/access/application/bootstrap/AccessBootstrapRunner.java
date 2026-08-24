package cn.ac.fage.accessmesh.access.application.bootstrap;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 空库 bootstrap 触发器（T-ACCESS-020，access-service-architecture.md §14.2）。
 * <p>
 * 默认关闭（{@code access.bootstrap.enabled=false}）；启用时在启动尾段触发一次事务化
 * initializer，密码缺失/空白 fail-fast 阻止启动。仅负责触发——固定图检测/创建全部在
 * {@link AccessBootstrapInitializer} 的单事务内完成。异常向上传播使 SpringApplication
 * 启动失败（幂等状态③ fail-fast 语义）。
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "access.bootstrap", name = "enabled", havingValue = "true")
public class AccessBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccessBootstrapRunner.class);

    private final AccessBootstrapProperties properties;
    private final AccessBootstrapInitializer initializer;

    public AccessBootstrapRunner(AccessBootstrapProperties properties, AccessBootstrapInitializer initializer) {
        this.properties = properties;
        this.initializer = initializer;
    }

    @Override
    public void run(ApplicationArguments args) {
        String adminPassword = properties.getAdminPassword();
        if (adminPassword == null || adminPassword.isBlank()) {
            // 不携带密码值（含长度），避免间接泄露
            throw new IllegalStateException(
                "access.bootstrap.enabled=true 但 ACCESS_BOOTSTRAP_ADMIN_PASSWORD 缺失或为空白（fail-fast）");
        }
        log.info("Bootstrap enabled: seeding first admin graph (tenant {})", BootstrapGraphDefinition.TENANT_ID);
        TenantContextHolder.setTenantId(BootstrapGraphDefinition.TENANT_ID);
        try {
            initializer.initialize(adminPassword);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
