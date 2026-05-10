package cn.ac.fage.accessmesh.permission.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.lang.reflect.Method;

/**
 * 异步任务执行配置类
 * <p>
 * 配置Spring异步任务的异常处理器。
 * 处理@Async方法中未捕获的异常，特别是void返回类型的方法。
 * </p>
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

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