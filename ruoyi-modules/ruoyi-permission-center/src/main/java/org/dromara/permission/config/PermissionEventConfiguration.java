package org.dromara.permission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class PermissionEventConfiguration {

    @Bean("permissionConflictEventExecutor")
    public TaskExecutor permissionConflictEventExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("permission-conflict-");
        executor.setConcurrencyLimit(4);
        return executor;
    }
}
