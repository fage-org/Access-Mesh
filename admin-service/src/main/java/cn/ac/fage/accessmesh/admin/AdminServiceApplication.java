package cn.ac.fage.accessmesh.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 管理服务应用程序入口
 * <p>
 * Admin Service 微服务的 Spring Boot 主启动类。
 * 提供用户管理、角色管理、菜单管理、组织管理等后台管理功能。
 * 启用 Feign 客户端、定时任务和 MyBatis Mapper 扫描。
 * </p>
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@MapperScan("cn.ac.fage.accessmesh.admin.mapper")
public class AdminServiceApplication {

    /**
     * 应用程序主入口
     * <p>
     * 启动 Spring Boot 应用，初始化所有配置和组件。
     * </p>
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
