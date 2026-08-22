package cn.ac.fage.accessmesh.access.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 路径契约快照测试（T-ACCESS-011 验收 3：契约回归）。
 * <p>
 * 快照基线生成方式：扫描全部 Controller 注解得到全量路径，并与两份权威契约文档
 * 及「归并前代码路径」（git 5f1e65dd5^：admin-service + permission-center 共 210 条）
 * 双向核对后的结果——归并后恰为 198 条；相对归并前仅减少 12 条且全部有
 * 设计决策背书（11 条 /sync-task/*：T-ACCESS-005 退役内部同步子系统；
 * /audit-log/page：T-ACCESS-007 确认前端与代码零引用后删除），无意外丢失、无新增。
 * </p>
 * <p>
 * 文档差异核对结论（api-contract.md §5 + admin-service-api-contract.md §4）：
 * 文档声明未实现的 4 条中 3 条为设计明确「已移除不实现」
 * （update-child/children-save/rebuild），1 条待 T-PERM-034（sub-perm-allowed-types）；
 * 代码存在而 api-contract §5 未列的 2 条（auth/query-permission-tree、
 * permission-view/effective-permission-codes）在 implementation.md §7.5 与
 * org-user-permission-contract.md v1.4 有权威定义（§5 清单欠账，非代码漂移）。
 * 路径增删必须显式更新本快照并在任务卡记录依据。
 * </p>
 */
class HttpApiPathSnapshotTest {

    private static final String BASE_PACKAGE = "cn.ac.fage.accessmesh.access";

    /** 归并后全量路径快照（POST + JSON Body；外部路径经 Gateway /admin、/perm StripPrefix=1 到达）。 */
    private static final Set<String> EXPECTED_PATHS = Set.of("""
/api/perm/abstract-role/create
/api/perm/abstract-role/detail
/api/perm/abstract-role/extra-roles/add
/api/perm/abstract-role/extra-roles/list
/api/perm/abstract-role/extra-roles/remove
/api/perm/abstract-role/full-sync
/api/perm/abstract-role/list
/api/perm/abstract-role/move
/api/perm/abstract-role/remove
/api/perm/abstract-role/sync
/api/perm/abstract-role/tree
/api/perm/abstract-role/update
/api/perm/abstract-user/create
/api/perm/abstract-user/detail
/api/perm/abstract-user/full-sync
/api/perm/abstract-user/list
/api/perm/abstract-user/remove
/api/perm/abstract-user/sync
/api/perm/abstract-user/update
/api/perm/auth/batch-check
/api/perm/auth/check
/api/perm/auth/check-interface
/api/perm/auth/interface-snapshot
/api/perm/auth/query-permission-tree
/api/perm/auth/query-resources
/api/perm/auth/query-scopes
/api/perm/biz-domain/create
/api/perm/biz-domain/detail
/api/perm/biz-domain/list
/api/perm/biz-domain/remove
/api/perm/biz-domain/update
/api/perm/conflict-rule/create
/api/perm/conflict-rule/detail
/api/perm/conflict-rule/detect
/api/perm/conflict-rule/list
/api/perm/conflict-rule/remove
/api/perm/conflict-rule/update
/api/perm/domain-config/detail
/api/perm/domain-config/list
/api/perm/domain-config/remove
/api/perm/domain-config/save
/api/perm/log/change/list
/api/perm/log/operation/list
/api/perm/operation-permission/create
/api/perm/operation-permission/detail
/api/perm/operation-permission/list
/api/perm/operation-permission/remove
/api/perm/operation-permission/update
/api/perm/permission-condition/create
/api/perm/permission-condition/detail
/api/perm/permission-condition/list
/api/perm/permission-condition/remove
/api/perm/permission-condition/update
/api/perm/permission-view/effective-permission-codes
/api/perm/permission-view/effective-permissions
/api/perm/permission-view/effective-roles
/api/perm/permission-view/explain
/api/perm/permission-view/recent-changes
/api/perm/permission-view/resource-tree
/api/perm/permission-view/resource-users
/api/perm/permission-view/role-permissions
/api/perm/resource-api-mapping/create
/api/perm/resource-api-mapping/list
/api/perm/resource-api-mapping/remove
/api/perm/resource-api-mapping/update
/api/perm/resource-dependency/batch-sync
/api/perm/resource-dependency/check
/api/perm/resource-dependency/create
/api/perm/resource-dependency/graph
/api/perm/resource-dependency/list
/api/perm/resource-dependency/remove
/api/perm/resource-dependency/update
/api/perm/resource-entity/batch-create
/api/perm/resource-entity/create
/api/perm/resource-entity/detail
/api/perm/resource-entity/full-sync
/api/perm/resource-entity/list
/api/perm/resource-entity/move
/api/perm/resource-entity/remove
/api/perm/resource-entity/sync
/api/perm/resource-entity/tree
/api/perm/resource-entity/update
/api/perm/role-resource-permission/add-child
/api/perm/role-resource-permission/apply-grant-plan
/api/perm/role-resource-permission/children
/api/perm/role-resource-permission/list
/api/perm/role-resource-permission/remove-child
/api/perm/role-resource-permission/revoke
/api/perm/role-resource-permission/save
/api/perm/service-config/apis
/api/perm/service-config/detail
/api/perm/service-config/list
/api/perm/service-config/remove
/api/perm/service-config/save
/api/perm/service-config/sync
/api/perm/system-config/detail
/api/perm/system-config/list
/api/perm/system-config/save
/api/perm/type-definition/create
/api/perm/type-definition/detail
/api/perm/type-definition/list
/api/perm/type-definition/remove
/api/perm/type-definition/update
/api/perm/user-role/assign
/api/perm/user-role/batch-assign
/api/perm/user-role/full-sync
/api/perm/user-role/list
/api/perm/user-role/revoke
/api/perm/user-role/sync
/auth/captcha
/auth/login
/auth/login/sms
/auth/logout
/auth/oauth2/authorize
/auth/oauth2/refresh
/auth/oauth2/revoke
/auth/oauth2/token
/auth/oauth2/userinfo
/auth/user-menu
/auth/userinfo
/config/delete
/config/detail
/config/page
/config/update
/dict/data/create
/dict/data/delete
/dict/data/list
/dict/data/update
/dict/type/create
/dict/type/delete
/dict/type/list
/dict/type/page
/file/delete
/file/detail
/file/download
/file/page
/file/upload
/job/create
/job/delete
/job/detail
/job/log/page
/job/page
/job/toggle
/job/trigger
/job/update
/login-log/page
/menu/create
/menu/delete
/menu/detail
/menu/tree
/menu/update
/notice/create
/notice/delete
/notice/detail
/notice/my-notices
/notice/page
/notice/publish
/notice/read
/notice/update
/oauth2/client/create
/oauth2/client/delete
/oauth2/client/detail
/oauth2/client/page
/oauth2/client/update
/org-tree-config/create
/org-tree-config/delete
/org-tree-config/detail
/org-tree-config/page
/org-tree-config/set-default
/org-tree-config/update
/org/create
/org/delete
/org/detail
/org/page
/org/tree
/org/update
/org/users
/role/create
/role/grant-menu
/role/list
/role/my-info
/role/revoke-menu
/user-org/assign
/user-org/list
/user-org/remove
/user-org/set-primary
/user-role/assign
/user-role/list
/user-role/revoke
/user/create
/user/delete
/user/detail
/user/enable
/user/member-candidates
/user/page
/user/reset-password
/user/update
/user/user-menus
""".strip().split("\n"));

    /** 已按设计决策退役的路径前缀/路径（快照必须不含；负向防回归）。 */
    private static final List<String> RETIRED_PATHS = List.of(
        // T-ACCESS-005：内部同步子系统退役（/admin/sync-task/* 经网关 StripPrefix 后即本服务 /sync-task/*）
        "/sync-task/list", "/sync-task/page", "/sync-task/detail", "/sync-task/delete",
        "/sync-task/due", "/sync-task/reset", "/sync-task/retry-now", "/sync-task/mark-success",
        "/sync-task/mark-failed", "/sync-task/batch-status", "/sync-task/rebuild-from-fact",
        // T-ACCESS-007：审计日志查询统一走 /api/perm/log/*
        "/audit-log/page"
    );

    /** 扫描 classpath 上全部 Controller（含 @RestController 元注解）并拼装全量路径。 */
    private Set<String> scanControllerPaths() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<String> paths = new TreeSet<>();
        for (Class<?> clazz : controllerClasses()) {
            RequestMapping classMapping =
                AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping.class);
            String base = "";
            if (classMapping != null && classMapping.path().length > 0) {
                base = classMapping.path()[0];
            }
            for (Method m : clazz.getDeclaredMethods()) {
                RequestMapping merged =
                    AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (merged == null) {
                    continue;
                }
                String sub = merged.path().length > 0 ? merged.path()[0] : "";
                paths.add((base + sub).replace("//", "/"));
            }
        }
        return paths;
    }

    @Test
    @DisplayName("全量路径快照：Controller 映射恰为 198 条，增删必须显式更新快照")
    void controllerPaths_matchSnapshot() throws Exception {
        Set<String> actual = scanControllerPaths();

        assertThat(actual).as("代码路径必须与快照完全一致（新增/删除路径须更新快照并记录依据）")
            .containsExactlyInAnyOrderElementsOf(EXPECTED_PATHS);
    }

    @Test
    @DisplayName("POST-only：全部映射方法为 POST（@PostMapping），无 GET/PUT/DELETE/PATCH")
    void allMappingsArePost() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : controllerClasses()) {
            for (Method m : clazz.getDeclaredMethods()) {
                RequestMapping merged =
                    AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (merged == null) {
                    continue;
                }
                for (RequestMethod method : merged.method()) {
                    if (method != RequestMethod.POST) {
                        violations.add(m.toGenericString() + " -> " + method);
                    }
                }
            }
        }
        assertThat(violations).as("对外接口统一 POST + JSON Body").isEmpty();
    }

    @Test
    @DisplayName("无 RESTful 路径参数：映射路径不含 {...} 模板变量")
    void noPathVariableTemplates() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String path : scanControllerPaths()) {
            if (path.contains("{") || path.contains("}")) {
                violations.add(path);
            }
        }
        assertThat(violations).as("ID 一律放请求体，禁止 RESTful 路径参数").isEmpty();
    }

    @Test
    @DisplayName("@RequestParam 仅允许出现在文件上传方法（MultipartFile 例外）")
    void requestParamOnlyForFileUpload() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : controllerClasses()) {
            for (Method m : clazz.getDeclaredMethods()) {
                boolean hasFileParam = Arrays.stream(m.getParameters())
                    .map(Parameter::getType)
                    .anyMatch(t -> t == MultipartFile.class || t == MultipartFile[].class);
                boolean hasRequestParam = Arrays.stream(m.getParameters())
                    .anyMatch(p -> p.isAnnotationPresent(RequestParam.class));
                if (hasRequestParam && !hasFileParam) {
                    violations.add(m.toGenericString());
                }
            }
        }
        assertThat(violations).as("@RequestParam 仅文件上传例外允许").isEmpty();
    }

    @Test
    @DisplayName("退役接口负向断言：/sync-task/* 与 /audit-log/page 无任何 Controller 映射")
    void retiredPaths_haveNoControllerMappings() throws Exception {
        Set<String> actual = scanControllerPaths();
        List<String> resurrected = new ArrayList<>();
        for (String retired : RETIRED_PATHS) {
            if (actual.contains(retired)) {
                resurrected.add(retired);
            }
        }
        // sync-task 前缀整体防回归：任何以 /sync-task/ 开头的映射都算复活
        for (String path : actual) {
            if (path.startsWith("/sync-task/") && !resurrected.contains(path)) {
                resurrected.add(path);
            }
        }
        assertThat(resurrected).as("退役接口不得注册任何 Controller 映射").isEmpty();
    }

    /**
     * classpath 上的 Controller 类；排除测试类与测试夹具
     * （test-classes 目录 / 嵌套 Stub 类，如 SyncEndpointAuthIT$StubActuatorController）。
     */
    private List<Class<?>> controllerClasses() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(BASE_PACKAGE)) {
            String name = bd.getBeanClassName();
            if (name.contains("$")) {
                continue;
            }
            Class<?> clazz = Class.forName(name);
            java.net.URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
            if (location != null && location.toString().contains("test-classes")) {
                continue;
            }
            classes.add(clazz);
        }
        return classes;
    }
}
