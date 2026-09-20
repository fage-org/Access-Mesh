package cn.ac.fage.accessmesh.access;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * access-service 统一启动入口
 * <p>
 * 归并 admin-service 与 permission-center（两个旧部署单元已退役）后的唯一 Spring Boot 主启动类。
 * 扫描 17 顶层能力包全部子包（12 能力包 + sync + engine + projection + bootstrap + infrastructure）。
 * </p>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan({
    "cn.ac.fage.accessmesh.access.auth.mapper",
    "cn.ac.fage.accessmesh.access.user.mapper",
    "cn.ac.fage.accessmesh.access.org.mapper",
    "cn.ac.fage.accessmesh.access.menu.mapper",
    "cn.ac.fage.accessmesh.access.role.mapper",
    "cn.ac.fage.accessmesh.access.grant.mapper",
    "cn.ac.fage.accessmesh.access.resource.mapper",
    "cn.ac.fage.accessmesh.access.type.mapper",
    "cn.ac.fage.accessmesh.access.domain.mapper",
    "cn.ac.fage.accessmesh.access.rule.mapper",
    "cn.ac.fage.accessmesh.access.audit.mapper",
    "cn.ac.fage.accessmesh.access.platform.mapper",
    "cn.ac.fage.accessmesh.access.sync.mapper",
    "cn.ac.fage.accessmesh.access.infrastructure.mapper",
    "cn.ac.fage.accessmesh.access.infrastructure.credential.mapper"
})
public class AccessServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessServiceApplication.class, args);
    }
}
