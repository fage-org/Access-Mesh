package cn.ac.fage.accessmesh.probe.log4j2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T-ACCESS-100 r2 回归锁（codex sol r2 P2-3）：SDK 闭包不得向宿主带入任何默认
 * 日志实现/桥接件。
 * <p>
 * 探针宿主自选 Log4j2 并按 Spring Boot 官方推荐在自身 starter-web 边排除
 * spring-boot-starter-logging；SDK 两 starter 声明在前（最坏仲裁形态）。若 SDK
 * 任何 compile 边仍通向 spring-boot-starter（→ starter-logging → Logback +
 * log4j-to-slf4j + jul-to-slf4j），宿主的剪除被击穿：SLF4J 多 Provider、宿主
 * 后端被静默替换甚至桥接环——这是「SDK 对宿主日志零参与」目标的反向形态
 * （sdk-host-probe 的 Logback 宿主形态锁不到它：宿主本来就要 Logback）。
 * </p>
 * <p>类路径存在性断言（Class.forName）= 依赖解析锁的等价离线验证；辅以 SLF4J
 * 实际绑定 Log4j2 的运行时判定。</p>
 */
class HostChosenLog4j2BackendProbeTest {

    @Test
    @DisplayName("宿主自选 Log4j2 保持，SDK 未带入 Logback/桥接件")
    void sdkTransfersNoDefaultLoggingIntoLog4j2Host() {
        // 阳性对照：宿主自己的 Log4j2 实现在场
        assertDoesNotThrow(() -> Class.forName("org.apache.logging.log4j.core.impl.Log4jContextFactory"),
            "探针宿主应自带 Log4j2（本模块显式声明），缺失说明探针自身声明损坏");

        // SDK 闭包不得带入 Logback（宿主已在自身边排除默认 logging，唯一来源=SDK）
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("ch.qos.logback.classic.LoggerContext"),
            "SDK 闭包带入了 Logback：宿主自选后端面临被替换（经 spring-boot-starter 传递边）");

        // log4j-to-slf4j（Log4j2 API→SLF4J 桥，与宿主 log4j-slf4j2-impl 成环）不得在场
        // （标记类 SLF4JProvider 实证属于 log4j-to-slf4j；SLF4JServiceProvider/
        // SLF4J2ServiceProvider 属于宿主自带的 log4j-slf4j2-impl，不作缺席断言）
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.apache.logging.slf4j.SLF4JProvider"),
            "SDK 闭包带入了 log4j-to-slf4j：与宿主 log4j-slf4j2-impl 构成桥接环");

        // jul-to-slf4j 不得在场（starter-logging 组合件，SDK 带入指示）
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.slf4j.bridge.SLF4JBridgeHandler"),
            "SDK 闭包带入了 jul-to-slf4j（starter-logging 组合件外溢）");

        // 运行时语义锁：SLF4J 实际绑定宿主 Log4j2（无竞争 Provider 时唯一可能；
        // log4j-slf4j2-impl 的 ILoggerFactory 实现=Log4jLoggerFactory，jar 条目实证）
        assertInstanceOf(org.apache.logging.slf4j.Log4jLoggerFactory.class,
            LoggerFactory.getILoggerFactory(),
            "SLF4J 实际选定的 Provider 不是宿主 Log4j2：SDK 引入了竞争后端");
    }
}
