package cn.ac.fage.accessmesh.access.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

/**
 * 任务执行专用线程资源配置（T-ACCESS-009 用户决策：专用任务执行器）
 * <p>
 * {@code sys_job} 业务执行使用独立的有界命名执行器 {@code accessTaskExecutor}，
 * 与审计异步（{@code accessAsyncExecutor}，T-ACCESS-007）容量隔离：
 * 长任务阻塞不得挤压审计日志写入。容量参数唯一来源为 application.yml
 * {@code access.task.executor.*}（运维可调），代码不设硬编码容量。
 * </p>
 * <p>
 * 租约续租使用专用调度器 {@code taskLeaseRenewalScheduler}（AI 三轮复评修复）：
 * 共享调度器上的对账/接管扫描会同步做数据库 IO，阻塞超过租约窗口时续租停摆
 * 会被其他实例接管，破坏「最多一个活动执行者」——续租是正确性路径，必须与
 * 维护任务线程隔离。共享调度器 {@code taskScheduler}（AI 四轮复评修复：声明
 * 任何 TaskScheduler Bean 都会使 Boot 自动配置退让，必须显式补齐）承接
 * @Scheduled 与 cron 触发，池大小由 {@code spring.task.scheduling.pool.size}
 * （application.yml，设 2）调节。
 * </p>
 */
@Configuration
public class TaskExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorConfig.class);

    @Value("${access.task.executor.core-size:2}")
    private int corePoolSize;

    @Value("${access.task.executor.max-size:4}")
    private int maxPoolSize;

    @Value("${access.task.executor.queue-capacity:100}")
    private int queueCapacity;

    @Value("${access.task.executor.keep-alive:60s}")
    private Duration keepAlive;

    @Bean(name = "accessTaskExecutor")
    public ThreadPoolTaskExecutor accessTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds((int) keepAlive.getSeconds());
        executor.setThreadNamePrefix("access-task-");
        // 队列满 + 线程满：记告警后抛 RejectedExecutionException——必须抛出，
        // ThreadPoolTaskExecutor 才会转成 TaskRejectedException 通知提交方
        // （JobServiceImpl 停续租并写回 FAILED，未超限时由接管扫描按至少一次语义重试）。
        // 只记日志不抛会让已启动的续租永远续下去，任务永久 RUNNING 无法接管。
        // 线程池停机阶段同样抛出，避免停机中启动长任务。
        executor.setRejectedExecutionHandler((runnable, poolExecutor) -> {
            log.error("任务执行线程池已满或已关闭，拒绝本次执行提交（提交方将写回 FAILED，"
                + "未超限时由接管扫描器重试）: {}", runnable);
            throw new java.util.concurrent.RejectedExecutionException(
                "access task executor rejected: pool full or shutdown");
        });
        return executor;
    }

    /**
     * 共享任务调度器（AI 四轮复评修复）
     * <p>
     * 本模块声明任何 {@code TaskScheduler} Bean 都会使 Boot 的
     * TaskSchedulingAutoConfiguration 退让（@ConditionalOnMissingBean）——
     * 若只声明续租调度器，全容器只剩那一个单线程调度器，@Scheduled
     * （对账/接管扫描）与 cron 触发会和续租挤同一线程，隔离形同虚设。
     * 因此显式声明共享调度器承接 @Scheduled 与 cron 触发，池大小沿用
     * {@code spring.task.scheduling.pool.size}（application.yml 设 2，
     * 维护任务在线程内做数据库 IO，默认单线程会互相饿死）。
     * Bean 名保持 {@code taskScheduler}：@Scheduled 解析按名称优先匹配。
     * </p>
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${spring.task.scheduling.pool.size:1}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduling-");
        // 已取消的调度节点立即移出延迟队列（任务更新/停用/删除高频 cancel 后
        // 不残留到原触发时间），与续租调度器一致
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /**
     * 租约续租专用调度器（T-ACCESS-009）
     * <p>
     * 单线程足够（每次续租是一条快速 UPDATE，按 20s 周期轮转）；
     * {@code removeOnCancelPolicy} 及时移除已取消的续租任务，防句柄堆积。
     * 独立于共享调度器：对账/接管扫描在共享调度器线程上做数据库 IO，
     * 阻塞时不得拖垮续租（租约丢失误触发接管，破坏「最多一个活动执行者」）。
     * </p>
     */
    @Bean(name = "taskLeaseRenewalScheduler")
    public ThreadPoolTaskScheduler taskLeaseRenewalScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("access-task-lease-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
