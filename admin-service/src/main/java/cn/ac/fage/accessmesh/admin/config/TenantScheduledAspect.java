package cn.ac.fage.accessmesh.admin.config;

import cn.ac.fage.accessmesh.common.mybatis.TenantAwareScheduled;
import cn.ac.fage.accessmesh.common.mybatis.TenantIdProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * AOP aspect that intercepts methods annotated with {@link TenantAwareScheduled}
 * and executes the business logic once for each active tenant.
 *
 * <p>For each tenant:
 * <ol>
 *   <li>Sets {@code TenantContextHolder.setTenantId(tenantId)} so MyBatis-Flex
 *       auto-tenant-filter applies.</li>
 *   <li>Invokes the scheduled method.</li>
 *   <li>Clears the context in {@code finally} block to prevent leakage.</li>
 * </ol>
 */
@Aspect
@Component
public class TenantScheduledAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantScheduledAspect.class);

    private final TenantIdProvider tenantIdProvider;

    public TenantScheduledAspect(TenantIdProvider tenantIdProvider) {
        this.tenantIdProvider = tenantIdProvider;
    }

    @Around("@annotation(cn.ac.fage.accessmesh.common.mybatis.TenantAwareScheduled)")
    public Object aroundTenantAwareScheduled(ProceedingJoinPoint joinPoint) throws Throwable {
        Set<Long> tenantIds = tenantIdProvider.getTenantIds();

        if (tenantIds.isEmpty()) {
            log.warn("No active tenants found. Skipping scheduled task: {}",
                joinPoint.getSignature().toShortString());
            return null;
        }

        log.info("Running tenant-aware scheduled task {} for {} tenant(s)",
            joinPoint.getSignature().toShortString(), tenantIds.size());

        for (Long tenantId : tenantIds) {
            try {
                TenantContextHolder.setTenantId(tenantId);
                log.debug("Executing scheduled task for tenant {}: {}",
                    tenantId, joinPoint.getSignature().toShortString());
                joinPoint.proceed();
            } catch (Exception e) {
                log.error("Scheduled task failed for tenant {}: {}",
                    tenantId, joinPoint.getSignature().toShortString(), e);
            } finally {
                TenantContextHolder.clear();
            }
        }

        return null;
    }
}
