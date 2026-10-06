package cn.ac.fage.accessmesh.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例服务应用程序入口
 * <p>
 * Example Service 微服务的 Spring Boot 主启动类。
 * 作为权限中心的接入示例演示服务：接口级鉴权由 Gateway 承担；本服务自
 * T-ACCESS-061 起引入 perm-client starter 承载 §8.6 业务最终检查（服务凭证通道，
 * ReportController/BusinessPermChecker），SDK 经自动装配注册验签过滤器——
 * 「不依赖 perm-client」为 T-API-001 时点旧口径，已随收编失效（2026-10-06 清扫）。
 * </p>
 */
@SpringBootApplication
public class ExampleServiceApplication {

    /**
     * 应用程序主入口
     * <p>
     * 启动 Spring Boot 应用，初始化所有配置和组件。
     * </p>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ExampleServiceApplication.class, args);
    }
}
