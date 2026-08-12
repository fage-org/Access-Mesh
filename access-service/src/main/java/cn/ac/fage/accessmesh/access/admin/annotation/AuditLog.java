package cn.ac.fage.accessmesh.access.admin.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * <p>
 * 标记需要进行审计日志记录的方法。
 * 通过AuditLogAspect切面自动记录方法调用信息。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作模块
     *
     * @return 模块名称
     */
    String module() default "";

    /**
     * 操作动作
     *
     * @return 动作名称
     */
    String action() default "";

    /**
     * 目标资源类型
     *
     * @return 资源类型
     */
    String targetType() default "";
}
