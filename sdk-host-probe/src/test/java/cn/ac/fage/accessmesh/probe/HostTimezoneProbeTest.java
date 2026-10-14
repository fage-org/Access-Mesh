package cn.ac.fage.accessmesh.probe;

import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-ACCESS-100 回归锁②：SDK 不改写宿主 JVM 默认时区。
 * <p>
 * 平台的 UTC 强制（{@code UtcTimezoneEnvironmentPostProcessor}）经
 * {@code META-INF/spring.factories} 的 EnvironmentPostProcessor 注册生效——
 * 注册在 common 内时，任何引入 common jar 的应用（含外部宿主）都会在环境准备阶段
 * 被无条件改写默认时区，即外部持续审查 P2 的第二侵入项。收口后注册由平台应用
 * 各自声明，SDK 传递闭包内不再携带任何 EPP 注册文件。</p>
 * <p>探针把默认时区显式置为非 UTC（Asia/Shanghai）后启动真实 Spring 上下文：
 * 若 EPP 仍被传递，环境准备阶段会翻转为 UTC——断言保持 Shanghai 即红。</p>
 */
class HostTimezoneProbeTest {

    private static final String HOST_ZONE = "Asia/Shanghai";

    @Test
    @DisplayName("宿主 Spring 上下文启动后 JVM 默认时区保持宿主自选值")
    void bootDoesNotRewriteJvmDefaultTimezone() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(HOST_ZONE));
        try {
            SpringApplication app = new SpringApplication(ProbeHostConfig.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            try (ConfigurableApplicationContext ignored = app.run()) {
                // 上下文启动即完成环境准备（EPP 若在场已执行）；无额外交互
            }
            assertEquals(HOST_ZONE, TimeZone.getDefault().getID(),
                "SDK 传递了 UTC 时区 EPP：宿主进程默认时区被无条件改写");
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
