package cn.ac.fage.accessmesh.probe;

import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T-ACCESS-100 回归锁①：SDK 不替换/不混入宿主默认日志后端。
 * <p>
 * 探针宿主自选 WebFlux 技术栈，其默认日志后端为 Logback（spring-boot-starter-webflux
 * → spring-boot-starter → spring-boot-starter-logging）。SDK 传递闭包中不得出现任何
 * log4j2 实现件——否则形成「宿主 Logback + SDK log4j-slf4j2-impl」SLF4J 双 Provider
 * （轻则后端被静默替换、log4j2-spring.xml 全部失效，重则桥接环 LoggingException），
 * 即外部持续审查 P2 的第一侵入项。宿主 starter-logging 自带的 log4j-to-slf4j 桥
 * 属宿主自选件、且仅与 SDK 泄漏的 log4j-core 组合成环，故锁 SDK 侧三件即可。</p>
 * <p>以 {@code Class.forName} 做类路径存在性断言（依赖解析锁的等价离线验证形态），
 * 辅以 SLF4J 实际选定 Provider 的运行时判定。</p>
 */
class HostLoggingBackendProbeTest {

    @Test
    @DisplayName("宿主默认 Logback 后端保持，SDK 未传递 log4j2 实现件")
    void sdkTransfersNoLoggingBackend() {
        // 阳性对照：宿主自己的 Logback 在场（防探针 pom 自身缺声明导致整套断言假绿）
        assertDoesNotThrow(() -> Class.forName("ch.qos.logback.classic.LoggerContext"),
            "探针宿主应自带 Logback（webflux starter 默认后端），缺失说明探针自身声明损坏");

        // log4j-core（实现本体）不得经 SDK 传递
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.apache.logging.log4j.core.impl.Log4jContextFactory"),
            "SDK 传递了 log4j-core：宿主默认后端面临被替换（common 的 log4j2 starter 传递边泄漏）");

        // log4j-slf4j2-impl（SLF4J→Log4j2 Provider，双 Provider 冲突的一端）不得在场
        // （该 jar 含 SLF4J2ServiceProvider 与 SLF4JServiceProvider 两个类，均作标记）
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.apache.logging.slf4j.SLF4J2ServiceProvider"),
            "SDK 传递了 log4j-slf4j2-impl：与宿主 Logback 构成 SLF4J 双 Provider");
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.apache.logging.slf4j.SLF4JServiceProvider"),
            "SDK 传递了 log4j-slf4j2-impl（第二标记类）");

        // log4j-to-slf4j/logback 不作缺席断言：宿主 starter-logging 自带同款（自选件），
        // 类路径无法区分来源——「SDK 不新增默认 logging 来源」由 sdk-host-probe-log4j2
        // 的 Log4j2 宿主形态锁定（该形态宿主已剪除 starter-logging，在场即 SDK 带入）

        // 运行时语义锁：无第二 Provider 竞争时，SLF4J 实际绑定宿主 Logback
        assertInstanceOf(LoggerContext.class, LoggerFactory.getILoggerFactory(),
            "SLF4J 实际选定的 Provider 不是宿主 Logback：SDK 引入了竞争后端");
    }
}
