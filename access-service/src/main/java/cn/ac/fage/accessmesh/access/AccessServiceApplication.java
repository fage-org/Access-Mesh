package cn.ac.fage.accessmesh.access;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * access-service 统一启动入口
 * <p>
 * 归并 admin-service 与 permission-center 后的唯一 Spring Boot 主启动类。
 * 扫描 admin、permission、application、infrastructure 全部子包。
 * </p>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan({
    "cn.ac.fage.accessmesh.access.admin.mapper",
    "cn.ac.fage.accessmesh.access.permission.mapper",
    "cn.ac.fage.accessmesh.access.infrastructure.mapper",
    "cn.ac.fage.accessmesh.access.application.query.mapper"
})
public class AccessServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessServiceApplication.class, args);
    }
}
