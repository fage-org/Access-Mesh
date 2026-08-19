package cn.ac.fage.accessmesh.access.permission.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务执行配置类
 * <p>
 * 提供有界线程池执行 @Async 任务（独立日志异步写入），并处理未捕获异常。
 * T-ACCESS-007 落地架构 §8.2：操作日志异步写入使用有界线程池；
 * 队列满时不得静默丢弃，降级为调用者线程同步写入并记录告警日志。
 * 本线程池同时承载 auditDomainService.asyncRecordLog 等独立日志异步写入。
 * </p>
 * <p>
 * 线程池容量参数唯一来源为 application.yml 的 spring.task.execution.pool
 * （用户决策，T-ACCESS-007）：本类仅通过 @Value 读取该配置构造 executor，
 * 不另设硬编码容量，避免与 Spring Boot 自动装配形成双源不一致；
 * 代码侧只补充 Spring Boot 默认 AbortPolicy 缺失的「队列满同步降级 + 告警」语义。
 * </p>
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${spring.task.execution.pool.core-size}")
    private int corePoolSize;

    @Value("${spring.task.execution.pool.max-size}")
    private int maxPoolSize;

    @Value("${spring.task.execution.pool.queue-capacity}")
    private int queueCapacity;

    @Value("${spring.task.execution.pool.keep-alive}")
    private Duration keepAlive;

    @Value("${spring.task.execution.thread-name-prefix}")
    private String threadNamePrefix;

    /**
     * 异步任务执行器（有界线程池，注册为 Spring Bean）
     * <p>
     * 容量参数取自 application.yml spring.task.execution.pool（运维可调）；
     * 队列满且线程池满时，RejectedExecutionHandler 降级为调用者线程同步执行
     * （同步写入日志，不丢失），并输出告警日志便于监控（T-ACCESS-007 用户决策：
     * 监控指标仅以错误/告警日志呈现，不引入 Micrometer）。
     * 注册为 Bean 后线程池生命周期（初始化/关闭）由容器统一管理：
     * 容器经 {@code afterPropertiesSet()} 初始化一次、停机时调用 {@code destroy()} 优雅关闭。
     * 方法体内不再显式调 {@code initialize()}——{@code ThreadPoolTaskExecutor.initialize()}
     * 在 Spring 6.1 会无条件重建 executor，若此处显式调用，容器随后对 Bean 再调
     * afterPropertiesSet() 会二次创建，旧线程池泄漏。初始化统一交由容器完成。
     * 拒绝处理等价 {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}：
     * 线程池已关闭（停机中）时丢弃任务不再执行，避免停机阶段在关闭中的线程池上
     * 执行新任务。
     * </p>
     *
     * @return 有界异步线程池
     */
    @Bean(name = "accessAsyncExecutor")
    public Executor accessAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds((int) keepAlive.getSeconds());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler((runnable, poolExecutor) -> {
            if (poolExecutor.isShutdown()) {
                // 线程池已关闭（应用停机）：不执行任务，等价 CallerRunsPolicy 语义
                log.warn("异步任务线程池已关闭，丢弃任务: {}", runnable);
                return;
            }
            log.warn("异步任务线程池已满（core={}, max={}, queue={}），降级为同步执行: {}",
                corePoolSize, maxPoolSize, queueCapacity, runnable);
            // 队列满 + 线程满：调用者线程直接运行（等价 CallerRunsPolicy），保证日志不丢失
            runnable.run();
        });
        return executor;
    }

    /**
     * 返回容器托管的异步执行器（与 {@link #accessAsyncExecutor()} 同一实例，
     * @Configuration 代理保证单例；避免 AsyncConfigurer 与 Bean 注册形成双实例）。
     */
    @Override
    public Executor getAsyncExecutor() {
        return accessAsyncExecutor();
    }

    /**
     * 获取异步任务异常处理器
     * <p>
     * 返回自定义的异常处理器，用于捕获@Async方法执行过程中的异常。
     * 异常会被记录到日志中，便于排查问题。
     * </p>
     *
     * @return 异步异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            /**
             * 处理未捕获的异步异常
             * <p>
             * 当@Async方法抛出异常时，记录异常信息到日志。
             * 包含方法名称、参数和异常详情。
             * </p>
             *
             * @param ex    抛出的异常
             * @param method 异步方法
             * @param params 方法参数
             */
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("异步方法 {} 执行失败，参数: {}",
                    method.getName(), params, ex);
                // TODO: 可扩展为发送告警通知（钉钉、邮件等）
            }
        };
    }
}
