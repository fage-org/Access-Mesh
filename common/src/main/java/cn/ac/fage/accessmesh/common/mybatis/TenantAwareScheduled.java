package cn.ac.fage.accessmesh.common.mybatis;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 租户感知定时任务注解
 * <p>
 * 标记定时任务方法为租户感知，使方法为每个活跃租户执行一次。
 * 业务逻辑会在TenantContextHolder设置租户ID后执行。
 * </p>
 *
 * <p>使用示例：
 * <pre>{@code
 * @TenantAwareScheduled
 * @Scheduled(fixedDelay = 30000)
 * public void processRetries() { ... }
 * }</pre>
 * </p>
 *
 * <p>要求：服务模块必须注册TenantIdProvider Bean。
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TenantAwareScheduled {
}