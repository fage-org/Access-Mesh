package cn.ac.fage.accessmesh.common.mybatis;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a scheduled method as tenant-aware.
 *
 * <p>Methods annotated with {@code @TenantAwareScheduled} will be wrapped so that
 * the business logic executes once for each active tenant, with
 * {@link TenantContextHolder} (service-specific) set appropriately.
 *
 * <p>Usage:
 * <pre>{@code
 * @TenantAwareScheduled
 * @Scheduled(fixedDelay = 30000)
 * public void processRetries() { ... }
 * }</pre>
 *
 * <p>Requires a {@link TenantIdProvider} bean to be registered in the service.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TenantAwareScheduled {
}
