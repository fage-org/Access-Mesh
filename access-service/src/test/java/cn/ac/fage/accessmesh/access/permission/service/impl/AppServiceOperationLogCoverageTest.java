package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AppService 操作日志覆盖与契约校验（T-ACCESS-007 评审修复）。
 * <p>
 * <b>permission 域强制全覆盖</b>：扫描 {@code permission.service.impl} 包下全部
 * {@code *AppServiceImpl} 的 public + {@code @Transactional} 非 readOnly 写方法，
 * 断言必须标注 {@link OperationLog}。曾硬编码 15 个类清单导致 4 个 Sync 实现
 * （AbstractRole/AbstractUser/ResourceEntity/UserRole）的 8 个 sync/fullSync 写入口
 * 漏标（P1 绕过原因），现改为包扫描消除清单漂移。
 * </p>
 * <p>
 * <b>契约校验</b>：对已标注方法统一校验——module 三值化（ADMIN/PERMISSION/ACCESS）、
 * action 大写事件码、targetType 为小写物理表名（白名单见 {@link #KNOWN_TABLE_NAMES}，
 * 与 access-service.sql 同步）或逻辑对象码例外、summary 为合法 SpEL（纯文本单引号包裹）。
 * admin/application 域尚未强制全覆盖（登记遗留 T-ACCESS-014），仅校验已标注方法契约。
 * </p>
 */
class AppServiceOperationLogCoverageTest {

    /** permission 域 AppService 实现包（强制全覆盖） */
    private static final String PERMISSION_PACKAGE = "cn.ac.fage.accessmesh.access.permission.service.impl";

    /** admin 域 Service 实现包（已标注方法契约校验） */
    private static final String ADMIN_PACKAGE = "cn.ac.fage.accessmesh.access.admin.service.impl";

    /** application 域写服务包 + 查询服务包（已标注方法契约校验） */
    private static final List<String> APPLICATION_PACKAGES = List.of(
        "cn.ac.fage.accessmesh.access.application.impl",
        "cn.ac.fage.accessmesh.access.application.query.impl"
    );

    /** module 三值化（access-service-architecture §8.2，按事务边界判定） */
    private static final Set<String> MODULES = Set.of("ADMIN", "PERMISSION", "ACCESS");

    /**
     * targetType 逻辑对象码例外（无物理表，显式登记；新增需同步登记到
     * {@link OperationLog} 注解 Javadoc）：
     * <ul>
     *   <li>{@code oauth2_token}：OAuth2 token 以 JWT（jti）+ Redis 黑名单存储，无对应表，
     *       OAUTH2_TOKEN_REVOKE 用该码标识被撤销的 token 对象。</li>
     * </ul>
     */
    private static final Set<String> TARGET_TYPE_EXCEPTIONS = Set.of("oauth2_token");

    /**
     * 物理表名白名单（access-service.sql 全部 33 表）。targetType 必须命中该集合
     * 或 {@link #TARGET_TYPE_EXCEPTIONS}，防止遗留非表名值（BATCH/SINGLE/oauth2_client 等）回潮。
     */
    private static final Set<String> KNOWN_TABLE_NAMES = Set.of(
        "sys_oauth2_client", "sys_login_log", "sys_user", "sys_org", "sys_org_tree_config",
        "sys_user_org", "sys_menu", "sys_dict_type", "sys_dict_data", "sys_notice",
        "sys_user_notice", "sys_file", "sys_job", "sys_job_log", "system_config",
        "operation_log", "type_definition", "biz_domain", "abstract_user", "abstract_role",
        "operation_permission", "resource_entity", "resource_api_mapping", "service_config",
        "permission_condition", "user_role", "sync_metadata", "role_resource_permission",
        "domain_config", "resource_dependency", "permission_conflict_rule",
        "permission_change_log", "sys_task_execution"
    );

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void shouldAnnotateAllTransactionalWriteMethodsInPermissionDomain() {
        List<Class<?>> classes = scanPackage(PERMISSION_PACKAGE);
        assertTrue(classes.size() >= 19,
            "permission.service.impl 应扫描到全部 AppService 实现（含 4 个 Sync），实际 " + classes.size());

        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : classes) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!isWriteMethod(method)) {
                    continue;
                }
                OperationLog opLog = method.getAnnotation(OperationLog.class);
                if (opLog == null) {
                    violations.add("缺少 @OperationLog: " + clazz.getSimpleName() + "#" + method.getName());
                    continue;
                }
                assertValidContract(clazz, method, opLog, violations);
            }
        }

        assertEquals(List.of(), violations,
            () -> "permission 域写方法缺 @OperationLog 或契约违规: " + String.join("; ", violations));
    }

    @Test
    void shouldValidateContractOfAnnotatedMethodsInAllDomains() {
        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : scanPackages(ADMIN_PACKAGE, APPLICATION_PACKAGES)) {
            for (Method method : clazz.getDeclaredMethods()) {
                OperationLog opLog = method.getAnnotation(OperationLog.class);
                if (opLog == null || !isWriteMethod(method)) {
                    continue;
                }
                assertValidContract(clazz, method, opLog, violations);
            }
        }

        assertEquals(List.of(), violations,
            () -> "admin/application 域已标注方法契约违规: " + String.join("; ", violations));
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** 是否为入口级写方法：public + @Transactional 且非 readOnly */
    private static boolean isWriteMethod(Method method) {
        if (!Modifier.isPublic(method.getModifiers())) {
            return false;
        }
        Transactional tx = method.getAnnotation(Transactional.class);
        return tx != null && !tx.readOnly();
    }

    /** 校验 @OperationLog 五属性契约，违规写入 violations */
    private void assertValidContract(Class<?> clazz, Method method, OperationLog opLog,
                                     List<String> violations) {
        String where = clazz.getSimpleName() + "#" + method.getName();

        if (!MODULES.contains(opLog.module())) {
            violations.add(where + " module='" + opLog.module() + "' 非三值化(ADMIN/PERMISSION/ACCESS)");
        }
        if (opLog.action() == null || !opLog.action().matches("[A-Z][A-Z0-9_]+")) {
            violations.add(where + " action='" + opLog.action() + "' 非大写事件码");
        }
        String targetType = opLog.targetType();
        if (targetType == null || !targetType.matches("[a-z][a-z0-9_]*")
            || (!KNOWN_TABLE_NAMES.contains(targetType) && !TARGET_TYPE_EXCEPTIONS.contains(targetType))) {
            violations.add(where + " targetType='" + targetType + "' 非物理表名且未登记例外");
        }
        if (opLog.summary() == null || opLog.summary().isBlank()) {
            violations.add(where + " summary 为空");
        } else {
            try {
                parser.parseExpression(opLog.summary());
            } catch (RuntimeException e) {
                violations.add(where + " summary 非法 SpEL: " + e.getMessage());
            }
        }
        if (opLog.targetId() != null && !opLog.targetId().isBlank()) {
            try {
                parser.parseExpression(opLog.targetId());
            } catch (RuntimeException e) {
                violations.add(where + " targetId 非法 SpEL: " + e.getMessage());
            }
        }
    }

    private static List<Class<?>> scanPackages(String adminPackage, List<String> applicationPackages) {
        List<Class<?>> result = new ArrayList<>(scanPackage(adminPackage));
        for (String pkg : applicationPackages) {
            result.addAll(scanPackage(pkg));
        }
        return result;
    }

    /** 扫描包下全部 *AppServiceImpl 具体类（含 query/impl 子包内查询服务，用于契约校验） */
    private static List<Class<?>> scanPackage(String pkg) {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
        Set<String> classNames = new LinkedHashSet<>();
        scanner.findCandidateComponents(pkg)
            .forEach(bd -> classNames.add(bd.getBeanClassName()));

        List<Class<?>> classes = new ArrayList<>();
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (!clazz.getSimpleName().endsWith("AppServiceImpl")
                    || Modifier.isAbstract(clazz.getModifiers())
                    || clazz.isInterface()
                    || clazz.isMemberClass()
                    || clazz.isAnonymousClass()) {
                    continue;
                }
                classes.add(clazz);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("无法加载扫描到的类: " + className, e);
            }
        }
        return classes;
    }
}
