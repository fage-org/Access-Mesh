package cn.ac.fage.accessmesh.access.infrastructure.task;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务可调用白名单注解（T-ACCESS-009）
 * <p>
 * 只有显式标注本注解的 public 方法才能被 {@code sys_job.invoke_target}
 * （格式 {@code beanName.methodName}）反射调用；未标注的方法一律拒绝，
 * 防止任务配置指向任意 Spring Bean 方法（认证/租户管理等）造成提权面
 * （T-ACCESS-009 用户决策：注解白名单）。
 * </p>
 * <p>
 * 唯一受支持签名：单一 {@link TaskExecutionContext} 参数（用户决策：必须接收
 * 上下文）——幂等执行键对任何任务都有传递通道，满足「外部副作用携带幂等键」
 * 的验收要求。放置于 infrastructure 包：与 {@code @OperationLog} 同理，
 * admin/permission 域任务方法都需要标注，置于某一域包会造成跨域依赖。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobInvocable {
}
