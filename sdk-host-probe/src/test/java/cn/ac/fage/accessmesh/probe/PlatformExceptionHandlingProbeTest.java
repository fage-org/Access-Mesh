package cn.ac.fage.accessmesh.probe;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-ACCESS-100 回归锁③：SDK 不向宿主安装平台全局异常处理器。
 * <p>
 * 平台的 {@code GlobalExceptionHandler}（业务错误码契约改写）与
 * {@code RResponseAdvice}（requestId 响应字段注入）经 common 的
 * {@code CommonAutoConfiguration}（组件扫描 exception/config 两包）安装；
 * 该自动配置注册在 common 的 AutoConfiguration.imports 时，外部宿主的无关联 API
 * 错误响应契约也会被平台处理器改写——即外部持续审查 P2 的第三侵入项。
 * 收口后该自动配置由平台应用各自的 imports 文件声明。</p>
 * <p>断言不引用平台类符号（GlobalExceptionHandler/RResponseAdvice 属 WebMvc 系，
 * 收口后非 WebMvc 宿主类路径本就不该有它们的依赖闭包）：以
 * {@code @RestControllerAdvice} 注解面（spring-web，WebFlux 宿主在场）+ Bean
 * 类型名前缀扫描双重锁定。</p>
 */
class PlatformExceptionHandlingProbeTest {

    @Test
    @DisplayName("宿主上下文无 @RestControllerAdvice Bean，无 common 包组件")
    void platformGlobalExceptionHandlingNotInstalled() {
        SpringApplication app = new SpringApplication(ProbeHostConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext ctx = app.run()) {
            assertTrue(ctx.getBeansWithAnnotation(RestControllerAdvice.class).isEmpty(),
                "SDK 向宿主安装了平台 @RestControllerAdvice 处理器：宿主无关 API 的错误契约被改写");
            assertTrue(Arrays.stream(ctx.getBeanDefinitionNames())
                    .map(ctx::getType)
                    .filter(java.util.Objects::nonNull)
                    .map(Class::getName)
                    .noneMatch(name -> name.startsWith("cn.ac.fage.accessmesh.common.")),
                "SDK 向宿主装配了平台 common 包组件（异常处理器/响应增强等）");
        }
    }
}
