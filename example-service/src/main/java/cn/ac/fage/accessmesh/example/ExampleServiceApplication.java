package cn.ac.fage.accessmesh.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例服务应用程序入口
 * <p>
 * Example Service 微服务的 Spring Boot 主启动类。
 * 作为权限中心的接入示例演示服务：接口级鉴权由 Gateway 承担（规范 §2.4
 * 服务内不重复鉴权），业务服务无需引入权限 SDK 即可被保护，因此本服务
 * 不依赖 perm-client / perm-data starter，也不做服务内二次鉴权。
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
