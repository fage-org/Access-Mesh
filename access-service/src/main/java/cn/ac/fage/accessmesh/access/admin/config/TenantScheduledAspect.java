package cn.ac.fage.accessmesh.access.admin.config;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;

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
 * 租户感知定时任务切面
 * <p>
 * AOP切面拦截标记{@link TenantAwareScheduled}注解的方法，
 * 为每个活跃租户执行一次业务逻辑。
 * </p>
 *
 * <p>对每个租户的执行流程：
 * <ol>
 *   <li>设置TenantContextHolder.setTenantId(tenantId)，使MyBatis-Flex自动租户过滤生效</li>
 *   <li>调用定时任务方法</li>
 *   <li>在finally块中清理上下文，防止泄漏</li>
 * </ol>
 * </p>
 */
@Aspect
@Component
public class TenantScheduledAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantScheduledAspect.class);

    private final TenantIdProvider tenantIdProvider;

    /**
     * 构造租户定时任务切面
     * <p>
     * 注入租户ID提供者用于获取活跃租户列表。
     * </p>
     *
     * @param tenantIdProvider 租户ID提供者
     */
    public TenantScheduledAspect(TenantIdProvider tenantIdProvider) {
        this.tenantIdProvider = tenantIdProvider;
    }

    /**
     * 切面拦截方法
     * <p>
     * 拦截标记TenantAwareScheduled注解的方法，
     * 为每个活跃租户执行一次方法调用。
     * 单个租户执行失败不影响其他租户的执行。
     * </p>
     *
     * @param joinPoint 切点连接点
     * @return 方法返回值（此处为null，因为多次执行）
     */
    @Around("@annotation(cn.ac.fage.accessmesh.common.mybatis.TenantAwareScheduled)")
    public Object aroundTenantAwareScheduled(ProceedingJoinPoint joinPoint) throws Throwable {
        Set<Long> tenantIds = tenantIdProvider.getTenantIds();

        // 检查是否有活跃租户
        if (tenantIds.isEmpty()) {
            log.warn("未找到活跃租户。跳过定时任务: {}",
                joinPoint.getSignature().toShortString());
            return null;
        }

        log.info("执行租户感知定时任务 {}，涉及 {} 个租户",
            joinPoint.getSignature().toShortString(), tenantIds.size());

        // 为每个租户执行定时任务
        for (Long tenantId : tenantIds) {
            try {
                TenantContextHolder.setTenantId(tenantId);
                log.debug("为租户 {} 执行定时任务: {}",
                    tenantId, joinPoint.getSignature().toShortString());
                joinPoint.proceed();
            } catch (Exception e) {
                log.error("租户 {} 定时任务执行失败: {}",
                    tenantId, joinPoint.getSignature().toShortString(), e);
            } finally {
                TenantContextHolder.clear();
            }
        }

        return null;
    }
}