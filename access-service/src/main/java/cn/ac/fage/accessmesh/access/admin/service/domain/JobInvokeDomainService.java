package cn.ac.fage.accessmesh.access.admin.service.domain;

import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;

/**
 * 任务调用目标执行领域服务（T-ACCESS-009）
 * <p>
 * 解析 {@code invoke_target}（格式 {@code beanName.methodName}）并反射调用。
 * 安全边界：仅允许调用显式标注 {@code @JobInvocable} 的 public 方法
 * （T-ACCESS-009 用户决策：注解白名单），防止任务配置指向任意 Bean 方法。
 * 唯一受支持签名为单一 {@link TaskExecutionContext} 参数（用户决策：
 * 必须接收上下文）——幂等执行键对任何任务都有传递通道，满足「外部副作用
 * 携带幂等键」的验收要求。
 * </p>
 */
public interface JobInvokeDomainService {

    /**
     * 执行调用目标
     *
     * @param invokeTarget 调用目标（beanName.methodName）
     * @param context      任务执行上下文（含幂等执行键）
     * @throws IllegalArgumentException 目标格式非法 / Bean 不存在 / 方法不存在 /
     *                                  方法未标注 @JobInvocable / 签名不支持
     * @throws RuntimeException         目标方法抛出的业务异常原样传播
     */
    void invoke(String invokeTarget, TaskExecutionContext context);
}
