package cn.ac.fage.accessmesh.access.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TaskExecutorConfig} 拒绝语义与调度器拓扑测试（T-ACCESS-009 AI 复评修复）。
 * <p>
 * 拒绝处理器必须抛出 RejectedExecutionException——只有异常传播，
 * ThreadPoolTaskExecutor 才会转成 TaskRejectedException 通知提交方。
 * <p>
 * 调度器拓扑（AI 四轮复评）：声明任何 TaskScheduler Bean 都会使 Boot 的
 * TaskSchedulingAutoConfiguration 退让，因此必须显式补齐共享 taskScheduler——
 * 验证容器中存在两个互不相同的调度器 Bean（共享承接 @Scheduled/cron 触发、
 * 专用承接租约续租），共享池大小跟随 spring.task.scheduling.pool.size。
 * </p>
 */
class TaskExecutorConfigTest {

    @Test
    void rejectionSurfacesAsTaskRejectedException() throws Exception {
        TaskExecutorConfig config = new TaskExecutorConfig();
        ReflectionTestUtils.setField(config, "corePoolSize", 1);
        ReflectionTestUtils.setField(config, "maxPoolSize", 1);
        ReflectionTestUtils.setField(config, "queueCapacity", 1);
        ReflectionTestUtils.setField(config, "keepAlive", Duration.ofSeconds(60));

        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
            config.accessTaskExecutor();
        // 非容器托管场景手动初始化（容器托管时由 afterPropertiesSet 完成）
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocker = () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            // 占满唯一线程 + 唯一队列位（任务阻塞直到释放）
            executor.execute(blocker);
            executor.execute(blocker);
            assertThatThrownBy(() -> executor.execute(blocker))
                .isInstanceOf(TaskRejectedException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void schedulerTopologyIsolatesRenewalFromSharedScheduler() {
        new ApplicationContextRunner()
            // 裸 runner 不带 Boot 的 Duration 转换（access.task.executor.keep-alive=60s 需要）
            .withInitializer(context -> context.getBeanFactory()
                .setConversionService(
                    org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(TaskExecutorConfig.class)
            .withPropertyValues("spring.task.scheduling.pool.size=2")
            .run(context -> {
                assertThat(context).hasBean("taskScheduler");
                assertThat(context).hasBean("taskLeaseRenewalScheduler");
                // 两个调度器必须是不同实例：续租隔离才真实成立
                assertThat(context.getBean("taskScheduler"))
                    .isNotSameAs(context.getBean("taskLeaseRenewalScheduler"));
                // 共享池大小跟随配置（承接 @Scheduled/cron 触发）
                ThreadPoolTaskScheduler shared =
                    (ThreadPoolTaskScheduler) context.getBean("taskScheduler");
                assertThat(shared.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
                // 续租专用调度器单线程
                ThreadPoolTaskScheduler renewal =
                    (ThreadPoolTaskScheduler) context.getBean("taskLeaseRenewalScheduler");
                assertThat(renewal.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
                // 类型候选恰好两个（显式声明替代自动配置，未产生第三个）
                assertThat(context.getBeansOfType(TaskScheduler.class)).hasSize(2);
            });
    }
}
