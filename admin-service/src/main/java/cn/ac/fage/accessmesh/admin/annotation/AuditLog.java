package cn.ac.fage.accessmesh.admin.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    String module() default "";

    String action() default "";

    String targetType() default "";
}
