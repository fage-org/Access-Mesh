package cn.ac.fage.accessmesh.access.infrastructure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限变更入口标记
 * <p>
 * 标在 AppService 写方法上，由 {@link PermissionChangeAspect} 拦截：
 * <ol>
 *   <li>入口绑定 {@code PermissionChangeContext}（ThreadLocal 累积器）</li>
 *   <li>业务方法体内通过 {@code PermissionChangeContext.mark*} 登记受影响范围</li>
 *   <li>事务提交后（afterCommit）统一 flush：evict 相关缓存 + 广播 {@code PermInvalidateEvent}</li>
 * </ol>
 * 与 {@link OperationLog} 并存，二者职责分离：{@code @OperationLog} 记入口审计，
 * {@code @PermissionChange} 驱动缓存失效与广播。
 * </p>
 * <p>
 * <b>铁律 P1-B</b>：标注本注解的方法体内禁止手写 {@code TransactionSynchronizationManager}，
 * afterCommit 注册由 AOP 框架统一完成。
 * </p>
 *
 * @see cn.ac.fage.accessmesh.access.permission.aop.PermissionChangeAspect
 * @see cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionChange {
}
