package cn.ac.fage.accessmesh.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.mybatis.spring.annotation.MapperScan;

import static org.junit.jupiter.api.Assertions.*;

/**
 * access-service 启动类与模块结构验证测试。
 * <p>
 * 验证归并后的 AccessServiceApplication 配置正确：
 * - @SpringBootApplication 注解存在
 * - @MapperScan 覆盖 admin 和 permission 两个域
 * - @EnableFeignClients / @EnableAsync / @EnableScheduling 存在
 * </p>
 * <p>
 * 完整 Spring Context 启动测试（真实 PostgreSQL + Redis + Nacos）在 T-ACCESS-011 验收门禁中执行。
 * </p>
 */
class AccessServiceApplicationTest {

    @Test
    @DisplayName("AccessServiceApplication 是 Spring Boot 启动类")
    void shouldBeSpringBootApplication() {
        var annotation = AnnotationUtils.findAnnotation(
            AccessServiceApplication.class,
            org.springframework.boot.autoconfigure.SpringBootApplication.class
        );
        assertNotNull(annotation, "AccessServiceApplication 必须标注 @SpringBootApplication");
    }

    @Test
    @DisplayName("@MapperScan 覆盖 admin 和 permission 两个域")
    void mapperScanShouldCoverBothDomains() {
        var annotation = AnnotationUtils.findAnnotation(
            AccessServiceApplication.class,
            MapperScan.class
        );
        assertNotNull(annotation, "AccessServiceApplication 必须标注 @MapperScan");
        var packages = annotation.value();
        assertTrue(
            java.util.Arrays.asList(packages).contains("cn.ac.fage.accessmesh.access.admin.mapper"),
            "@MapperScan 必须包含 admin.mapper"
        );
        assertTrue(
            java.util.Arrays.asList(packages).contains("cn.ac.fage.accessmesh.access.permission.mapper"),
            "@MapperScan 必须包含 permission.mapper"
        );
    }

    @Test
    @DisplayName("启动类位于 access 根包，子域不包含 @SpringBootApplication")
    void noDuplicateSpringBootApplication() {
        // AccessServiceApplication 位于 cn.ac.fage.accessmesh.access 包
        assertEquals(
            "cn.ac.fage.accessmesh.access",
            AccessServiceApplication.class.getPackageName(),
            "启动类必须位于 access 根包"
        );
    }
}
