package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.task.JobInvocable;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JobInvokeDomainServiceImpl} 单元测试（T-ACCESS-009）。
 * <p>
 * 覆盖 @JobInvocable 白名单安全边界：未标注方法拒绝调用（防提权）、
 * 仅支持单 TaskExecutionContext 参数签名（用户决策：必须接收上下文——
 * 幂等键必有传递通道）、幂等键透传与业务异常原样传播。
 * </p>
 */
class JobInvokeDomainServiceTest {

    private StaticApplicationContext applicationContext;
    private JobInvokeDomainServiceImpl service;
    private TaskExecutionContext context;

    /** 测试任务 Bean：提供白名单内/外方法与受支持/不受支持签名 */
    static class TestJobBean {
        int contextCalls;
        String lastExecutionKey;

        @JobInvocable
        public void runWithContext(TaskExecutionContext ctx) {
            contextCalls++;
            lastExecutionKey = ctx.executionKey();
        }

        public void notWhitelisted() {
            throw new IllegalStateException("must never be invoked");
        }

        @JobInvocable
        public void runNoArg() {
            throw new IllegalStateException("must never be invoked");
        }

        @JobInvocable
        public void runWithWrongSignature(String param) {
            throw new IllegalStateException("must never be invoked");
        }

        @JobInvocable
        public void failing(TaskExecutionContext ctx) {
            throw new IllegalStateException("business failure for " + ctx.executionKey());
        }
    }

    @BeforeEach
    void setUp() {
        applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("testJobBean", new TestJobBean());
        applicationContext.refresh();
        service = new JobInvokeDomainServiceImpl(applicationContext);
        context = new TaskExecutionContext(1L, 100L, "job:100:20260821T120000", 1,
            LocalDateTime.of(2026, 8, 21, 12, 0, 0));
    }

    @Test
    void shouldPassIdempotencyKeyThroughContext() {
        service.invoke("testJobBean.runWithContext", context);
        TestJobBean bean = applicationContext.getBean(TestJobBean.class);
        assertThat(bean.contextCalls).isEqualTo(1);
        assertThat(bean.lastExecutionKey).isEqualTo("job:100:20260821T120000");
    }

    @Test
    void shouldRejectNonWhitelistedMethod() {
        assertThatThrownBy(() -> service.invoke("testJobBean.notWhitelisted", context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not @JobInvocable");
    }

    @Test
    void shouldRejectNoArgSignature() {
        // 用户决策：必须接收上下文——无参任务无法携带幂等键，拒绝
        assertThatThrownBy(() -> service.invoke("testJobBean.runNoArg", context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expected single TaskExecutionContext parameter");
    }

    @Test
    void shouldRejectUnsupportedSignature() {
        assertThatThrownBy(() -> service.invoke("testJobBean.runWithWrongSignature", context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expected single TaskExecutionContext parameter");
    }

    @Test
    void shouldRejectUnknownBeanAndBadFormat() {
        assertThatThrownBy(() -> service.invoke("noSuchBean.run", context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bean not found");
        assertThatThrownBy(() -> service.invoke("illegal-format", context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expected 'beanName.methodName'");
        assertThatThrownBy(() -> service.invoke("  ", context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void shouldPropagateBusinessException() {
        assertThatThrownBy(() -> service.invoke("testJobBean.failing", context))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("business failure for job:100:20260821T120000");
    }

    @Test
    void shouldResolveAnnotationThroughCglibProxy() {
        // 事务化等被 AOP 代理的任务 Bean：代理类生成的方法不携带目标方法注解，
        // 白名单必须在目标类上解析、调用经代理对象保留代理语义
        java.util.concurrent.atomic.AtomicInteger adviceCalls =
            new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.aop.framework.ProxyFactory factory =
            new org.springframework.aop.framework.ProxyFactory(new TestJobBean());
        factory.setProxyTargetClass(true);
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            adviceCalls.incrementAndGet();
            return invocation.proceed();
        });
        Object proxy = factory.getProxy();

        StaticApplicationContext proxiedContext = new StaticApplicationContext();
        proxiedContext.getBeanFactory().registerSingleton("proxiedJobBean", proxy);
        proxiedContext.refresh();
        JobInvokeDomainServiceImpl proxiedService = new JobInvokeDomainServiceImpl(proxiedContext);

        proxiedService.invoke("proxiedJobBean.runWithContext", context);

        assertThat(adviceCalls.get()).isEqualTo(1);
        Object target;
        try {
            target = ((org.springframework.aop.framework.Advised) proxy)
                .getTargetSource().getTarget();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        TestJobBean targetBean = (TestJobBean) target;
        assertThat(targetBean.contextCalls).isEqualTo(1);
        assertThat(targetBean.lastExecutionKey).isEqualTo("job:100:20260821T120000");
    }
}
