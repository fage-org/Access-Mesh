package cn.ac.fage.accessmesh.gateway;

import cn.ac.fage.accessmesh.common.config.CommonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 网关应用程序入口
 * <p>
 * Gateway 微服务的 Spring Boot 主启动类。
 * 提供 API 网关功能，包括路由转发，认证校验，权限校验等。
 * 使用 Spring Cloud Gateway 实现响应式网关，启用服务发现。
 * </p>
 *
 * <p>T-ACCESS-100（2026-10-14）：common 不再携带自动装配注册（外部宿主引入权限 SDK
 * 不被装配平台组件），装配清单由各服务 META-INF/spring/...AutoConfiguration.imports
 * 自声明——Gateway 实际装配缓存两行。WebMvc 系 CommonAutoConfiguration
 * （GlobalExceptionHandler/RResponseAdvice）维持主类 exclude，且本服务 imports 将其
 * 登记为候选（「注册以排除」）：exclude 校验要求被排除类是自动装配候选，而 E2E 共享
 * 大类路径（子进程类路径含兄弟模块 target/classes）会经 access/example 注册文件带入
 * 该类——候选登记 + exclude 在两种类路径形态下均合法，净效果恒为不装配（平台
 * GlobalExceptionHandler 与本服务同名 Bean 冲突，T-ACCESS-003 既有防线，
 * 2026-10-14 T-ACCESS-100 双向红跑实证仍然承重）。</p>
 */
@SpringBootApplication(exclude = CommonAutoConfiguration.class)
@EnableDiscoveryClient
@EnableScheduling
public class GatewayApplication {

    /**
     * 应用程序主入口
     * <p>
     * 启动 Spring Boot 应用，初始化所有配置和组件。
     * </p>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
