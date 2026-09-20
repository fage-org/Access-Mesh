package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AppService 操作日志契约校验（T-ACCESS-025 收敛后口径；T-ACCESS-033 改能力包扫描面）。
 * <p>
 * <b>仅校验已标注方法</b>：对 12 能力包（capability-structure §8.1）service.impl 包中已标注
 * {@link OperationLog} 的方法统一校验——module 三值化（ADMIN/PERMISSION/ACCESS）、
 * action 大写事件码、targetType 为小写物理表名（白名单见 {@link #KNOWN_TABLE_NAMES}，
 * 与 access-service.sql 同步）或逻辑对象码例外、summary/targetId 为合法 SpEL
 * （纯文本单引号包裹）。engine.service（引擎对外查询编排）非能力包，不在扫描面（§8.4）。
 * </p>
 * <p>
 * <b>不再强制全覆盖（T-ACCESS-025）</b>：「所有事务写方法必须标注 @OperationLog」的
 * 包扫描强制断言与豁免登记机制已删除——写方法是否标注由写入口通用清单
 * （project-rules §写入口通用清单）规范指导，存量注解不强制新增，
 * 仅保持已标注语义（本类契约校验）。无事务方法不在校验范围（委托门面的审计由
 * 事务边界的 AppService 承载，登录/登出由 sys_login_log 承载，
 * access-service-architecture §8.2）。
 * </p>
 */
class AppServiceOperationLogCoverageTest {

    /** 12 能力包 service.impl 包集合（capability-structure §8.1/§8.4；engine 非能力包不在内） */
    private static final List<String> CAPABILITY_IMPL_PACKAGES = List.of(
        "cn.ac.fage.accessmesh.access.auth.service.impl",
        "cn.ac.fage.accessmesh.access.user.service.impl",
        "cn.ac.fage.accessmesh.access.org.service.impl",
        "cn.ac.fage.accessmesh.access.menu.service.impl",
        "cn.ac.fage.accessmesh.access.role.service.impl",
        "cn.ac.fage.accessmesh.access.grant.service.impl",
        "cn.ac.fage.accessmesh.access.resource.service.impl",
        "cn.ac.fage.accessmesh.access.type.service.impl",
        "cn.ac.fage.accessmesh.access.domain.service.impl",
        "cn.ac.fage.accessmesh.access.rule.service.impl",
        "cn.ac.fage.accessmesh.access.audit.service.impl",
        "cn.ac.fage.accessmesh.access.platform.service.impl"
    );

    /**
     * 逐能力包覆盖下限自证登记（§8.4：每能力包至少扫描到 1 个 ServiceImpl，
     * 或显式登记该能力无 ServiceImpl）——当前 12 能力包全部有实现类。
     */
    private static final Set<String> CAPABILITIES_WITHOUT_IMPL = Set.of();

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
     * 物理表名白名单（与 access-service.sql 同步）。targetType 必须命中该集合
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
        "permission_dependency_declaration", "service_manifest_sync", "resource_publication_state",
        "permission_change_log", "sys_task_execution"
    );

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void shouldValidateContractOfAnnotatedMethodsInAllCapabilityPackages() {
        // 契约校验对已标注 @OperationLog 的方法无条件生效——不依赖 @Transactional 写方法判定，
        // 覆盖 12 能力包 service.impl（含 OAuth2 token/refresh/revoke、Job trigger 等已标注但非事务入口）。
        // 逐能力包下限自证（§8.4）：每能力包至少扫描到 1 个 ServiceImpl，或显式登记无实现。
        for (String pkg : CAPABILITY_IMPL_PACKAGES) {
            String capability = pkg.substring(pkg.indexOf(".access.") + ".access.".length(),
                pkg.lastIndexOf(".service.impl"));
            List<Class<?>> scanned = scanPackage(pkg);
            if (CAPABILITIES_WITHOUT_IMPL.contains(capability)) {
                continue;
            }
            assertTrue(!scanned.isEmpty(),
                "能力包 " + capability + " 应至少扫描到 1 个 ServiceImpl（无实现须登记 CAPABILITIES_WITHOUT_IMPL），实际 0");
        }
        List<Class<?>> allCapabilities = new ArrayList<>();
        for (String pkg : CAPABILITY_IMPL_PACKAGES) {
            allCapabilities.addAll(scanPackage(pkg));
        }
        // 覆盖下限哨兵=逐能力包断言（上方循环）+ CAPABILITIES_WITHOUT_IMPL 显式登记；
        // 不设全局总量魔数——计划内入口退役（如 037 删 ConfigAppServiceImpl）会合法减员，
        // 裸总量下限会误报（外评 P3，2026-09-13）。

        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : allCapabilities) {
            for (Method method : clazz.getDeclaredMethods()) {
                OperationLog opLog = method.getAnnotation(OperationLog.class);
                if (opLog == null) {
                    continue;
                }
                assertValidContract(clazz, method, opLog, violations);
            }
        }

        assertEquals(List.of(), violations,
            () -> "能力包已标注方法契约违规: " + String.join("; ", violations));
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------
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

    /**
     * 扫描包下全部 Service 实现类，用于契约校验。
     * <p>
     * 匹配 {@code *ServiceImpl}（T-ACCESS-033 后统一为 {@code *AppServiceImpl}）。
     * </p>
     */
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
                if (!clazz.getSimpleName().endsWith("ServiceImpl")
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
