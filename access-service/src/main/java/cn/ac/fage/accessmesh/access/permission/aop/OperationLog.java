package cn.ac.fage.accessmesh.access.permission.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * <p>
 * 标记在 AppService 的写方法上，由 {@link OperationLogAspect} 自动拦截并记录入口级操作日志。
 * 内部动态日志（diff 快照、冲突通知）仍由 AuditDomainService 显式调用。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作所属模块
     */
    String module();

    /**
     * 操作动作
     */
    String action();

    /**
     * 目标类型（支持 SpEL 表达式，如 #req.serviceCode()）
     */
    String targetType();

    /**
     * 目标ID（SpEL 表达式，如 #req.id()）
     */
    String targetId();

    /**
     * 操作摘要（SpEL 表达式，如 'created role ' + #req.name()）
     */
    String summary();
}
