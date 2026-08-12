package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.aop.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppServiceOperationLogCoverageTest {

    @Test
    void shouldAnnotateAllTransactionalWriteAppServiceMethods() {
        List<Class<?>> appServiceClasses = List.of(
            BizDomainAppServiceImpl.class,
            ConditionAppServiceImpl.class,
            ConflictRuleAppServiceImpl.class,
            DependencyAppServiceImpl.class,
            DomainConfigAppServiceImpl.class,
            GroupRoleAppServiceImpl.class,
            OperationAppServiceImpl.class,
            PermissionGrantAppServiceImpl.class,
            ResourceManageAppServiceImpl.class,
            RoleManageAppServiceImpl.class,
            ServiceConfigAppServiceImpl.class,
            ServiceSyncAppServiceImpl.class,
            SystemConfigAppServiceImpl.class,
            TypeDefinitionAppServiceImpl.class,
            UserManageAppServiceImpl.class
        );

        List<String> missingAnnotations = new ArrayList<>();
        for (Class<?> appServiceClass : appServiceClasses) {
            for (Method method : appServiceClass.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }

                Transactional transactional = method.getAnnotation(Transactional.class);
                if (transactional == null || transactional.readOnly()) {
                    continue;
                }

                if (!method.isAnnotationPresent(OperationLog.class)) {
                    missingAnnotations.add(appServiceClass.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(
            missingAnnotations.isEmpty(),
            () -> "Missing @OperationLog on transactional AppService methods: " + String.join(", ", missingAnnotations)
        );
    }
}