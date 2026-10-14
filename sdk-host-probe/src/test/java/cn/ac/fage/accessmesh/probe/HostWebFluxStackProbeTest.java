package cn.ac.fage.accessmesh.probe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T-ACCESS-100 回归锁④：SDK 不把 WebFlux 宿主推成 Servlet 应用。
 * <p>
 * common 以 compile 传递 {@code spring-boot-starter-web}（spring-webmvc + 内嵌
 * Tomcat）时，WebFlux 宿主的类路径同时出现两套 Web 栈——Spring Boot 应用类型
 * 推断按「webmvc 优先」裁决为 SERVLET，宿主的响应式服务器被静默替换为 Tomcat，
 * 即外部持续审查 P2 的第四侵入项。收口后 common 的 Web 栈依赖为 optional。</p>
 * <p>锁两层：{@link SpringApplication} 构造期类路径推断（公开 API
 * {@code getWebApplicationType()}，deduceFromClasspath 为包私有不可直用）判定
 * REACTIVE（行为面）；DispatcherServlet/Tomcat 类不可加载（类路径面）。</p>
 */
class HostWebFluxStackProbeTest {

    @Test
    @DisplayName("WebFlux 宿主应用类型推断保持 REACTIVE，无 Servlet 栈类")
    void webfluxHostNotForcedIntoServletStack() {
        assertEquals(WebApplicationType.REACTIVE,
            new SpringApplication(ProbeHostConfig.class).getWebApplicationType(),
            "SDK 传递了 spring-webmvc/Tomcat：WebFlux 宿主被推成 Servlet 应用");
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.springframework.web.servlet.DispatcherServlet"),
            "SDK 传递了 spring-webmvc（DispatcherServlet 在场）");
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.apache.catalina.core.StandardContext"),
            "SDK 传递了内嵌 Tomcat（catalina 在场）");
    }
}
