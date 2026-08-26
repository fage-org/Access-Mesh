package cn.ac.fage.accessmesh.common.timezone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.TimeZone;

/**
 * JVM 默认时区强制 UTC（T-ACCESS-024，用户决策：代码级强制）。
 * <p>
 * 项目时间语义为「{@code LocalDateTime} 全链路 UTC 墙钟」：审计字段等时间生产点遍布
 * {@code LocalDateTime.now()}，TIMESTAMPTZ 经 {@code TimestamptzLocalDateTimeTypeHandler}
 * 显式按 UTC 换算——若 JVM 默认时区不是 UTC，生产点墙钟与数据库 {@code DEFAULT now()}
 * （真 UTC 瞬时）会整体偏移。本处理器在环境准备阶段把默认时区固定为 UTC，
 * 部署侧无需任何 {@code -Duser.timezone} / {@code TZ} 约定，配错面归零。
 * </p>
 * <p>
 * 经 {@code META-INF/spring.factories} 注册，对所有引入 common 的 Spring 应用生效，
 * 包括 {@code @SpringBootTest} 测试上下文与 E2E 子进程（测试 JVM 不经过 main()，
 * 部署级方案罩不住，这是选代码级强制的决定性原因）。幂等：已是 UTC 则不动作。
 * 随之统一为 UTC 的行为面：日志时间戳、cron 调度时区（跨实例同时区约定自动闭合）。
 * 未来若需多时区部署支持，另立任务，不在本项目「不做跨时区支持」边界内扩展。
 * </p>
 */
public class UtcTimezoneEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String UTC_ZONE_ID = "UTC";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!UTC_ZONE_ID.equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone(UTC_ZONE_ID));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
