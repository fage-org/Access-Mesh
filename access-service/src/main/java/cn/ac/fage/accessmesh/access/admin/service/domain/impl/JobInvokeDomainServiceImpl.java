package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.service.domain.JobInvokeDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.task.JobInvocable;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;

/**
 * 任务调用目标执行领域服务实现（T-ACCESS-009）
 * <p>
 * 反射调用规则：
 * <ul>
 *   <li>目标格式必须为 {@code beanName.methodName}，两点/零点均拒绝；</li>
 *   <li>方法必须 public 且显式标注 {@code @JobInvocable}（白名单，防提权）；</li>
 *   <li>唯一受支持签名：单一 {@link TaskExecutionContext} 参数（用户决策：
 *       必须接收上下文——幂等执行键必有传递通道，无参签名拒绝）；</li>
 *   <li>目标方法抛出的异常去包装 InvocationTargetException 后原样传播，
 *       由执行编排层判定失败。</li>
 * </ul>
 * </p>
 */
@Service
public class JobInvokeDomainServiceImpl implements JobInvokeDomainService {

    private final ApplicationContext applicationContext;

    public JobInvokeDomainServiceImpl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void invoke(String invokeTarget, TaskExecutionContext context) {
        if (invokeTarget == null || invokeTarget.isBlank()) {
            throw new IllegalArgumentException("invoke_target is blank");
        }
        String[] parts = invokeTarget.trim().split("\\.");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                "invalid invoke_target '" + invokeTarget + "', expected 'beanName.methodName'");
        }
        String beanName = parts[0];
        String methodName = parts[1];

        Object bean;
        try {
            bean = applicationContext.getBean(beanName);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "invoke_target bean not found: '" + beanName + "'", e);
        }

        // 代理兼容：注解与签名在目标类上解析（代理类生成的方法不携带目标方法注解，
        // 事务化等被代理的任务 Bean 不能因此被误判未授权）；实际调用经
        // getMostSpecificMethod 换回代理对象上可反射调用的方法，保留代理语义
        // （如 @Transactional 事务边界）。
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
        Method targetMethod = findInvocableMethod(targetClass, methodName);
        Method invocableMethod = BridgeMethodResolver.findBridgedMethod(
            ClassUtils.getMostSpecificMethod(targetMethod, bean.getClass()));
        if (!invocableMethod.getDeclaringClass().isInstance(bean)) {
            // JDK 动态代理且方法未在接口上声明的极端配置：代理对象上不可调用
            throw new IllegalArgumentException(
                "method '" + methodName + "' is not invocable on the proxy of '"
                    + beanName + "' (declare it on the target class or interface)");
        }

        try {
            invocableMethod.invoke(bean, context);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 业务异常原样传播，不吞不换
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "failed to invoke " + invokeTarget, e);
        }
    }

    /**
     * 解析并校验目标方法：public + @JobInvocable 白名单 + 单 TaskExecutionContext 参数。
     */
    private Method findInvocableMethod(Class<?> beanClass, String methodName) {
        for (Method method : beanClass.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (method.getAnnotation(JobInvocable.class) == null) {
                throw new IllegalArgumentException(
                    "method '" + methodName + "' is not @JobInvocable, invocation rejected");
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1 && types[0] == TaskExecutionContext.class) {
                return method;
            }
            throw new IllegalArgumentException(
                "unsupported signature for '" + methodName
                    + "': expected single TaskExecutionContext parameter");
        }
        throw new IllegalArgumentException(
            "invoke_target method not found: '" + methodName + "'");
    }
}
