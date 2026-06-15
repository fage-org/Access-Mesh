package cn.ac.fage.accessmesh.permission;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 权限中心应用程序入口
 * <p>
 * Permission Center 微服务的 Spring Boot 主启动类。
 * 提供 API 接口级权限校验、角色管理、资源管理、权限授予等核心功能。
 * 启用 Feign 客户端、异步任务、定时任务和 MyBatis Mapper 扫描。
 * </p>
 */
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
@MapperScan("cn.ac.fage.accessmesh.permission.mapper")
public class PermissionCenterApplication {

    /**
     * 应用程序主入口
     * <p>
     * 启动 Spring Boot 应用，初始化所有配置和组件。
     * </p>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PermissionCenterApplication.class, args);
    }
}
