package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码分段契约扫描测试（T-ACCESS-011 验收 4）。
 * <p>
 * 证明归并未改变既有码值：以物理归并提交 5f1e65dd5 的父提交（归并前最后一版
 * admin-service / permission-center）为基线，固化全部 53 + 37 个错误码的
 * 「枚举名 → 码值」映射；当前枚举必须逐项包含且名称与码值的绑定不变
 * （同一编号不得改由其他枚举名承载，防止语义漂移）。归并后新增码
 * （10108~10111、20044~20047）不违反基线，只需满足分段规则。
 * </p>
 * <p>
 * 规则断言（access-service-architecture §9 / project-rules §1.2）：
 * 10001-19999 仅由 AdminErrorCode 定义；20001-29999 仅由 PermissionErrorCode 定义；
 * 90001-99999 仅由公共 GlobalErrorCode 定义；不存在 4xxxx 业务错误码；
 * 跨枚举（含枚举内）无重复码值。
 * </p>
 */
class ErrorCodeContractTest {

    private static final Map<String, Integer> PRE_MERGE_BASELINE = Map.ofEntries(
        // ===== 归并前 admin-service AdminErrorCode（git 5f1e65dd5^，53 项）=====
        Map.entry("ADMIN:USER_NOT_FOUND", 10001),
        Map.entry("ADMIN:USER_ALREADY_EXISTS", 10002),
        Map.entry("ADMIN:USER_DISABLED", 10003),
        Map.entry("ADMIN:USER_LOCKED", 10004),
        Map.entry("ADMIN:PASSWORD_INCORRECT", 10005),
        Map.entry("ADMIN:PHONE_ALREADY_EXISTS", 10006),
        Map.entry("ADMIN:CANNOT_DELETE_SELF", 10007),
        Map.entry("ADMIN:INVALID_PARAM", 10008),
        Map.entry("ADMIN:CANNOT_DISABLE_SELF", 10009),
        Map.entry("ADMIN:ORG_NOT_FOUND", 10101),
        Map.entry("ADMIN:ORG_CODE_EXISTS", 10102),
        Map.entry("ADMIN:ORG_HAS_CHILDREN", 10103),
        Map.entry("ADMIN:ORG_LEVEL_EXCEEDED", 10104),
        Map.entry("ADMIN:ORG_CROSS_TREE_MOVE", 10105),
        Map.entry("ADMIN:ORG_SINGLE_ASSOC_VIOLATION", 10106),
        Map.entry("ADMIN:ORG_TYPE_REQUIRED", 10107),
        Map.entry("ADMIN:MENU_NOT_FOUND", 10201),
        Map.entry("ADMIN:MENU_PERM_CODE_EXISTS", 10202),
        Map.entry("ADMIN:MENU_DEPTH_EXCEEDED", 10203),
        Map.entry("ADMIN:MENU_HAS_CHILDREN", 10204),
        Map.entry("ADMIN:DICT_TYPE_NOT_FOUND", 10301),
        Map.entry("ADMIN:DICT_TYPE_HAS_DATA", 10302),
        Map.entry("ADMIN:DICT_DATA_NOT_FOUND", 10303),
        Map.entry("ADMIN:NOTICE_NOT_FOUND", 10401),
        Map.entry("ADMIN:FILE_NOT_FOUND", 10501),
        Map.entry("ADMIN:FILE_UPLOAD_FAILED", 10502),
        Map.entry("ADMIN:FILE_TOO_LARGE", 10503),
        Map.entry("ADMIN:FILE_TYPE_NOT_ALLOWED", 10504),
        Map.entry("ADMIN:FILE_DELETE_FAILED", 10505),
        Map.entry("ADMIN:JOB_NOT_FOUND", 10601),
        Map.entry("ADMIN:CONFIG_NOT_FOUND", 10701),
        Map.entry("ADMIN:CONFIG_SYSTEM_IMMUTABLE", 10702),
        Map.entry("ADMIN:CLIENT_ID_EXISTS", 10801),
        Map.entry("ADMIN:CLIENT_NOT_FOUND", 10802),
        Map.entry("ADMIN:EXTERNAL_SERVICE_ERROR", 10900),
        Map.entry("ADMIN:CAPTCHA_INCORRECT", 10901),
        Map.entry("ADMIN:OAUTH2_CLIENT_INVALID", 10903),
        Map.entry("ADMIN:OAUTH2_REDIRECT_MISMATCH", 10904),
        Map.entry("ADMIN:OAUTH2_CODE_INVALID", 10905),
        Map.entry("ADMIN:OAUTH2_GRANT_TYPE_NOT_SUPPORTED", 10906),
        Map.entry("ADMIN:OAUTH2_CODE_VERIFIER_MISMATCH", 10907),
        Map.entry("ADMIN:OAUTH2_TOKEN_INVALID", 10908),
        Map.entry("ADMIN:OAUTH2_SCOPE_INVALID", 10909),
        Map.entry("ADMIN:OAUTH2_MISSING_CLIENT", 10910),
        Map.entry("ADMIN:OAUTH2_RESPONSE_TYPE_INVALID", 10911),
        Map.entry("ADMIN:ORG_TREE_CONFIG_NOT_FOUND", 11001),
        Map.entry("ADMIN:ORG_TREE_ROOT_NOT_RESOLVED", 11002),
        Map.entry("ADMIN:ORG_NOT_IN_DEFAULT_TREE", 11011),
        Map.entry("ADMIN:USER_NOT_IN_DEFAULT_TREE_SCOPE", 11012),
        Map.entry("ADMIN:USER_LOSE_DEFAULT_TREE_HOME", 11013),
        Map.entry("ADMIN:PRIMARY_MUST_BE_IN_DEFAULT_TREE", 11014),
        Map.entry("ADMIN:USER_ORG_RELATION_NOT_FOUND", 11015),
        Map.entry("ADMIN:USER_NOT_IN_OPERATOR_VISIBLE_SCOPE", 11016),

        // ===== 归并前 permission-center PermissionErrorCode（git 5f1e65dd5^，37 项）=====
        Map.entry("PERM:ROLE_NOT_FOUND", 20001),
        Map.entry("PERM:GRANT_REQUEST_EMPTY", 20002),
        Map.entry("PERM:ROLE_DISABLED", 20003),
        Map.entry("PERM:RESOURCE_NOT_FOUND", 20004),
        Map.entry("PERM:OPERATION_NOT_FOUND", 20005),
        Map.entry("PERM:CONDITION_NOT_FOUND", 20006),
        Map.entry("PERM:RESOURCE_TYPE_NOT_FOUND", 20007),
        Map.entry("PERM:RESOURCE_TYPE_OPERATION_MISMATCH", 20008),
        Map.entry("PERM:PARENT_PERMISSION_NOT_FOUND", 20009),
        Map.entry("PERM:PARENT_PERMISSION_NOT_TOP_LEVEL", 20010),
        Map.entry("PERM:SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED", 20011),
        Map.entry("PERM:RESOURCE_CODE_REQUIRED", 20012),
        Map.entry("PERM:CHILD_PERMISSION_NOT_FOUND", 20013),
        Map.entry("PERM:PERMISSION_NOT_CHILD", 20014),
        Map.entry("PERM:USER_NOT_FOUND", 20015),
        Map.entry("PERM:USER_ALREADY_EXISTS", 20016),
        Map.entry("PERM:DOMAIN_NOT_FOUND", 20017),
        Map.entry("PERM:DOMAIN_CONFIG_NOT_FOUND", 20018),
        Map.entry("PERM:DEPENDENCY_NOT_FOUND", 20019),
        Map.entry("PERM:CONFLICT_RULE_NOT_FOUND", 20020),
        Map.entry("PERM:TYPE_CODE_NOT_FOUND", 20021),
        Map.entry("PERM:ROLE_TYPE_MISMATCH", 20022),
        Map.entry("PERM:REQUEST_ITEMS_EMPTY", 20023),
        Map.entry("PERM:SYNC_RESOURCE_NOT_FOUND", 20024),
        Map.entry("PERM:RESOURCE_STATE_CONFLICT", 20025),
        Map.entry("PERM:SYSTEM_INIT_FAILED", 20026),
        Map.entry("PERM:VALIDATION_FAILED", 20027),
        Map.entry("PERM:TYPE_DEFINITION_NOT_FOUND", 20028),
        Map.entry("PERM:USER_ROLE_RELATION_NOT_FOUND", 20029),
        Map.entry("PERM:SYNC_TARGET_STATUS_INVALID", 20030),
        Map.entry("PERM:CONDITION_RULES_INVALID", 20031),
        Map.entry("PERM:CONFLICT_RULE_DUPLICATE", 20032),
        Map.entry("PERM:DIRECT_PERMISSION_CONFLICT", 20033),
        Map.entry("PERM:AUTO_DEP_READONLY", 20034),
        Map.entry("PERM:PERMISSION_NOT_FOUND", 20036),
        Map.entry("PERM:GRANT_CANNOT_DELEGATE", 20040),
        Map.entry("PERM:CONDITIONAL_PERMISSION_CANNOT_DELEGATE", 20041)
    );

    private static final int ADMIN_SEGMENT_MIN = 10001;
    private static final int ADMIN_SEGMENT_MAX = 19999;
    private static final int PERM_SEGMENT_MIN = 20001;
    private static final int PERM_SEGMENT_MAX = 29999;
    private static final int GLOBAL_SEGMENT_MIN = 90001;
    private static final int GLOBAL_SEGMENT_MAX = 99999;
    private static final int FORBIDDEN_BUSINESS_RANGE_MIN = 40000;
    private static final int FORBIDDEN_BUSINESS_RANGE_MAX = 49999;

    private static Map<String, Integer> adminByName() {
        return java.util.Arrays.stream(AdminErrorCode.values())
            .collect(Collectors.toMap(Enum::name, AdminErrorCode::getCode, (a, b) -> a, TreeMap::new));
    }

    private static Map<String, Integer> permByName() {
        return java.util.Arrays.stream(PermissionErrorCode.values())
            .collect(Collectors.toMap(Enum::name, PermissionErrorCode::getCode, (a, b) -> a, TreeMap::new));
    }

    private static Map<String, Integer> globalByName() {
        return java.util.Arrays.stream(GlobalErrorCode.values())
            .collect(Collectors.toMap(Enum::name, GlobalErrorCode::code, (a, b) -> a, TreeMap::new));
    }

    @Test
    @DisplayName("基线保持：归并前 53 个管理域码值与枚举名绑定不变（名称在、码值同、无重载）")
    void preMergeAdminBaseline_preserved() {
        Map<String, Integer> current = adminByName();
        Map<Integer, String> currentByCode = current.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Integer> e : PRE_MERGE_BASELINE.entrySet()) {
            String name = e.getKey().substring("ADMIN:".length());
            int code = e.getValue();
            if (code < ADMIN_SEGMENT_MIN || code > ADMIN_SEGMENT_MAX) {
                continue;
            }
            Integer currentCode = current.get(name);
            if (currentCode == null) {
                violations.add("枚举名缺失: " + name + "(" + code + ")");
            } else if (currentCode != code) {
                violations.add("码值改变: " + name + " " + code + " -> " + currentCode);
            } else if (!name.equals(currentByCode.get(code))) {
                violations.add("码值" + code + "改由其他枚举名承载: " + currentByCode.get(code));
            }
        }
        assertThat(violations).as("管理域归并前基线必须逐码保持").isEmpty();
    }

    @Test
    @DisplayName("基线保持：归并前 37 个权限域码值与枚举名绑定不变")
    void preMergePermissionBaseline_preserved() {
        Map<String, Integer> current = permByName();
        Map<Integer, String> currentByCode = current.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Integer> e : PRE_MERGE_BASELINE.entrySet()) {
            String name = e.getKey().substring("PERM:".length());
            int code = e.getValue();
            if (code < PERM_SEGMENT_MIN || code > PERM_SEGMENT_MAX) {
                continue;
            }
            Integer currentCode = current.get(name);
            if (currentCode == null) {
                violations.add("枚举名缺失: " + name + "(" + code + ")");
            } else if (currentCode != code) {
                violations.add("码值改变: " + name + " " + code + " -> " + currentCode);
            } else if (!name.equals(currentByCode.get(code))) {
                violations.add("码值" + code + "改由其他枚举名承载: " + currentByCode.get(code));
            }
        }
        assertThat(violations).as("权限域归并前基线必须逐码保持").isEmpty();
    }

    @Test
    @DisplayName("基线保持：公共 GlobalErrorCode 仍为 90001/99999 两项")
    void globalErrorCode_unchanged() {
        assertThat(globalByName()).containsOnly(
            Map.entry("VALIDATION_FAILED", 90001),
            Map.entry("SYSTEM_ERROR", 99999));
    }

    @Test
    @DisplayName("分段归属：1xxxx 仅 Admin 枚举、2xxxx 仅 Perm 枚举、9xxxx 仅公共枚举")
    void segments_ownedBySingleEnum() {
        List<String> violations = new ArrayList<>();

        adminByName().forEach((name, code) -> {
            if (code < ADMIN_SEGMENT_MIN || code > ADMIN_SEGMENT_MAX) {
                violations.add("AdminErrorCode." + name + "=" + code + " 越出 1xxxx 段");
            }
        });
        permByName().forEach((name, code) -> {
            if (code < PERM_SEGMENT_MIN || code > PERM_SEGMENT_MAX) {
                violations.add("PermissionErrorCode." + name + "=" + code + " 越出 2xxxx 段");
            }
        });
        globalByName().forEach((name, code) -> {
            if (code < GLOBAL_SEGMENT_MIN || code > GLOBAL_SEGMENT_MAX) {
                violations.add("GlobalErrorCode." + name + "=" + code + " 越出 9xxxx 段");
            }
        });
        assertThat(violations).as("错误码分段必须由归属枚举独占定义").isEmpty();
    }

    @Test
    @DisplayName("无 4xxxx：三个错误码枚举均不得定义 40000-49999 段业务错误码")
    void noBusinessErrorCodesIn4xxxxRange() {
        Map<String, Integer> all = new HashMap<>();
        all.putAll(adminByName());
        all.putAll(permByName());
        all.putAll(globalByName());

        List<String> violations = all.entrySet().stream()
            .filter(e -> e.getValue() >= FORBIDDEN_BUSINESS_RANGE_MIN && e.getValue() <= FORBIDDEN_BUSINESS_RANGE_MAX)
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.toList());
        assertThat(violations).as("不存在 4xxxx 业务错误码").isEmpty();
    }

    @Test
    @DisplayName("无重复：跨枚举与枚举内码值全局唯一")
    void noDuplicateCodesAcrossEnums() {
        Map<Integer, List<String>> owners = new TreeMap<>();
        adminByName().forEach((name, code) ->
            owners.computeIfAbsent(code, k -> new ArrayList<>()).add("AdminErrorCode." + name));
        permByName().forEach((name, code) ->
            owners.computeIfAbsent(code, k -> new ArrayList<>()).add("PermissionErrorCode." + name));
        globalByName().forEach((name, code) ->
            owners.computeIfAbsent(code, k -> new ArrayList<>()).add("GlobalErrorCode." + name));

        List<String> duplicates = owners.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> e.getKey() + " -> " + e.getValue())
            .collect(Collectors.toList());
        assertThat(duplicates).as("同一码值不得由多个错误码常量承载").isEmpty();
    }
}
