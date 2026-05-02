package cn.ac.fage.accessmesh.permission.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.lang.reflect.Method;

/**
 * Async task execution configuration.
 * Handles exceptions from @Async methods with void return type.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("Async method {} failed with params: {}",
                    method.getName(), params, ex);
                // TODO: 可扩展为发送告警通知（钉钉、邮件等）
            }
        };
    }
}