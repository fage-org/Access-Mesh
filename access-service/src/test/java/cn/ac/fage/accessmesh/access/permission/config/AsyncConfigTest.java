package cn.ac.fage.accessmesh.access.permission.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 有界线程池队列满降级测试（T-ACCESS-007 §8.2 验收标准 6）。
 * <p>
 * 验证 {@link AsyncConfig#getAsyncExecutor()} 构造的线程池在有界队列满且线程满时，
 * 不被默认 AbortPolicy 拒绝丢弃，而是降级到调用者线程同步执行（日志不丢失），
 * 同时不影响已提交到工作线程的任务。
 * </p>
 */
class AsyncConfigTest {

    private static void injectField(Object target, String name, Object value) throws Exception {
        Field f = AsyncConfig.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void shouldDegradeToCallerThreadWhenPoolFull() throws Exception {
        AsyncConfig config = new AsyncConfig();
        // 最小容量：1 线程 + 0 缓冲队列 → 第二个任务立即触发拒绝策略
        injectField(config, "corePoolSize", 1);
        injectField(config, "maxPoolSize", 1);
        injectField(config, "queueCapacity", 0);
        injectField(config, "keepAlive", Duration.ofSeconds(60));
        injectField(config, "threadNamePrefix", "access-test-async-");

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.getAsyncExecutor();
        // 模拟容器生命周期：@Bean 返回后由容器 afterPropertiesSet() 初始化
        // （评审 P3#8 后 accessAsyncExecutor() 不再显式 initialize，非 Spring 测试需手动补一次）
        executor.initialize();
        try {
            AtomicReference<String> workerThread = new AtomicReference<>();
            CountDownLatch holdWorker = new CountDownLatch(1);
            executor.execute(() -> {
                workerThread.set(Thread.currentThread().getName());
                try {
                    holdWorker.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            // 等待唯一工作线程领取任务
            while (workerThread.get() == null) {
                Thread.yield();
            }

            // 队列满 + 线程满：拒绝处理器在调用者线程同步执行（日志不丢失，不抛 RejectedExecutionException）
            AtomicReference<String> degradedThread = new AtomicReference<>();
            executor.execute(() -> degradedThread.set(Thread.currentThread().getName()));

            assertEquals(Thread.currentThread().getName(), degradedThread.get(),
                "队列满应降级到调用者线程同步执行，而非丢弃或抛 AbortPolicy 异常");

            holdWorker.countDown();
        } finally {
            executor.shutdown();
        }
    }
}
