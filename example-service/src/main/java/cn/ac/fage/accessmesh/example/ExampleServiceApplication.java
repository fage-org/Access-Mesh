package cn.ac.fage.accessmesh.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 示例服务应用程序入口
 * <p>
 * Example Service 微服务的 Spring Boot 主启动类。
 * 作为权限SDK集成的示例演示服务，展示如何使用perm-client-starter
 * 进行权限校验、用户同步、资源管理等操作。
 * 启用 Feign 客户端和 MyBatis Mapper 扫描。
 * </p>
 */
@SpringBootApplication
@EnableFeignClients
@MapperScan("cn.ac.fage.accessmesh.example.mapper")
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
