package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
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
 * 错误码分段契约扫描测试（T-ACCESS-011 验收 4；T-ACCESS-038 合一后重写）。
 * <p>
 * 证明归并及错误码面合一均未改变既有码值：以物理归并提交 5f1e65dd5 的父提交（归并前
 * 最后一版 admin-service / permission-center）为基线，固化归并前全部错误码的
 * 「枚举名 → 码值」映射（条目数以本常量断言为准，不另写计数）；当前合并册 AccessErrorCode 必须逐项包含且名称与码值的绑定
 * 不变（同一编号不得改由其他枚举名承载，防止语义漂移）。T-ACCESS-038 合一后，映射键 =
 * 合并册常量名单键——同名异号三组（USER_NOT_FOUND/USER_ALREADY_EXISTS/INVALID_PARAM）
 * 以 ADMIN_/PERM_ 段前缀消解，基线键随批改写为前缀名，编号对逐条保持。归并后新增码
 * （10108~10110、10205~10207、20042~20064）不违反基线，只需满足分段规则；归并后
 * 新增码的退役走 RETIRED_POST_MERGE_ADMIN 登记。
 * </p>
 * <p>
 * 规则断言（access-service-architecture §9 / project-rules §1.2，编号段判据）：
 * AccessErrorCode（合并册）全部码值必须落在 1xxxx 或 2xxxx 业务段内；
 * 90001-99999 仅由公共 GlobalErrorCode 定义；不存在 4xxxx 业务错误码；
 * 合并册内及与公共枚举间无重复码值。
 * </p>
 */
class ErrorCodeContractTest {

    /**
     * 显式退役清单（枚举名已从当前合并册移除，退役码值不得被复用）：
     * MENU_PERM_CODE_EXISTS(10202) 随 v3.5 菜单零权限化退役，
     * 唯一性校验由 uk_sys_menu_tenant_path / uk_sys_menu_tenant_resource
     * 及错误码 10205/10206 承接（T-ACCESS-015）。
     * CONFIG_NOT_FOUND(10701) / CONFIG_SYSTEM_IMMUTABLE(10702) 随 admin /config
     * 僵尸端点退役删除（T-ACCESS-037：前端/e2e/gateway 主代码零消费，
     * system_config 管理单入口 /api/perm/system-config，写面错误语义
     * 20047 CONFIG_KEY_NAMESPACE_INVALID 不受影响）。
     */
    private static final java.util.Set<String> RETIRED_ADMIN_NAMES =
        java.util.Set.of("MENU_PERM_CODE_EXISTS", "CONFIG_NOT_FOUND", "CONFIG_SYSTEM_IMMUTABLE");

    /**
     * 归并后新增码的退役登记（枚举名已从当前合并册移除，退役码值不得被复用）：
     * ROLE_API_RETIRED(10111) 随 admin 侧角色写代理端点删除退役（T-ADMIN-024：
     * /role/create、/role/grant-menu、/role/revoke-menu、/user-role/assign、
     * /user-role/revoke 经核实无存量调用方直删，不留兼容层）。
     * 不在 PRE_MERGE_BASELINE 内（归并后新增），故独立登记。
     */
    private static final Map<String, Integer> RETIRED_POST_MERGE_ADMIN =
        Map.of("ROLE_API_RETIRED", 10111);

    /**
     * 归并前基线（git 5f1e65dd5^，管理段 + 权限段全量条目）。T-ACCESS-038 合一后键 =
     * 合并册 AccessErrorCode 常量名：非碰撞项原名平移（去掉原 ADMIN:/PERM: 前缀），
     * 同名异号三组碰撞常量带 ADMIN_/PERM_ 段前缀——编号对与调用方语义原样保留由
     * 本基线逐条锁定。
     */
    private static final Map<String, Integer> PRE_MERGE_BASELINE = Map.ofEntries(
        // ===== 归并前 admin-service AdminErrorCode =====
        Map.entry("ADMIN_USER_NOT_FOUND", 10001),
        Map.entry("ADMIN_USER_ALREADY_EXISTS", 10002),
        Map.entry("USER_DISABLED", 10003),
        Map.entry("USER_LOCKED", 10004),
        Map.entry("PASSWORD_INCORRECT", 10005),
        Map.entry("PHONE_ALREADY_EXISTS", 10006),
        Map.entry("CANNOT_DELETE_SELF", 10007),
        Map.entry("ADMIN_INVALID_PARAM", 10008),
        Map.entry("CANNOT_DISABLE_SELF", 10009),
        Map.entry("ORG_NOT_FOUND", 10101),
        Map.entry("ORG_CODE_EXISTS", 10102),
        Map.entry("ORG_HAS_CHILDREN", 10103),
        Map.entry("ORG_LEVEL_EXCEEDED", 10104),
        Map.entry("ORG_CROSS_TREE_MOVE", 10105),
        Map.entry("ORG_SINGLE_ASSOC_VIOLATION", 10106),
        Map.entry("ORG_TYPE_REQUIRED", 10107),
        Map.entry("MENU_NOT_FOUND", 10201),
        Map.entry("MENU_PERM_CODE_EXISTS", 10202),
        Map.entry("MENU_DEPTH_EXCEEDED", 10203),
        Map.entry("MENU_HAS_CHILDREN", 10204),
        Map.entry("DICT_TYPE_NOT_FOUND", 10301),
        Map.entry("DICT_TYPE_HAS_DATA", 10302),
        Map.entry("DICT_DATA_NOT_FOUND", 10303),
        Map.entry("NOTICE_NOT_FOUND", 10401),
        Map.entry("FILE_NOT_FOUND", 10501),
        Map.entry("FILE_UPLOAD_FAILED", 10502),
        Map.entry("FILE_TOO_LARGE", 10503),
        Map.entry("FILE_TYPE_NOT_ALLOWED", 10504),
        Map.entry("FILE_DELETE_FAILED", 10505),
        Map.entry("FILE_PATH_ILLEGAL", 10506),
        Map.entry("FILE_READ_FAILED", 10507),
        Map.entry("JOB_NOT_FOUND", 10601),
        Map.entry("CONFIG_NOT_FOUND", 10701),
        Map.entry("CONFIG_SYSTEM_IMMUTABLE", 10702),
        Map.entry("CLIENT_ID_EXISTS", 10801),
        Map.entry("CLIENT_NOT_FOUND", 10802),
        Map.entry("EXTERNAL_SERVICE_ERROR", 10900),
        Map.entry("CAPTCHA_INCORRECT", 10901),
        Map.entry("OAUTH2_CLIENT_INVALID", 10903),
        Map.entry("OAUTH2_REDIRECT_MISMATCH", 10904),
        Map.entry("OAUTH2_CODE_INVALID", 10905),
        Map.entry("OAUTH2_GRANT_TYPE_NOT_SUPPORTED", 10906),
        Map.entry("OAUTH2_CODE_VERIFIER_MISMATCH", 10907),
        Map.entry("OAUTH2_TOKEN_INVALID", 10908),
        Map.entry("OAUTH2_SCOPE_INVALID", 10909),
        Map.entry("OAUTH2_MISSING_CLIENT", 10910),
        Map.entry("OAUTH2_RESPONSE_TYPE_INVALID", 10911),
        Map.entry("ORG_TREE_CONFIG_NOT_FOUND", 11001),
        Map.entry("ORG_TREE_ROOT_NOT_RESOLVED", 11002),
        Map.entry("ORG_NOT_IN_DEFAULT_TREE", 11011),
        Map.entry("USER_NOT_IN_DEFAULT_TREE_SCOPE", 11012),
        Map.entry("USER_LOSE_DEFAULT_TREE_HOME", 11013),
        Map.entry("PRIMARY_MUST_BE_IN_DEFAULT_TREE", 11014),
        Map.entry("USER_ORG_RELATION_NOT_FOUND", 11015),
        Map.entry("USER_NOT_IN_OPERATOR_VISIBLE_SCOPE", 11016),

        // ===== 归并前 permission-center PermissionErrorCode =====
        Map.entry("ROLE_NOT_FOUND", 20001),
        Map.entry("GRANT_REQUEST_EMPTY", 20002),
        Map.entry("ROLE_DISABLED", 20003),
        Map.entry("RESOURCE_NOT_FOUND", 20004),
        Map.entry("OPERATION_NOT_FOUND", 20005),
        Map.entry("CONDITION_NOT_FOUND", 20006),
        Map.entry("RESOURCE_TYPE_NOT_FOUND", 20007),
        Map.entry("RESOURCE_TYPE_OPERATION_MISMATCH", 20008),
        Map.entry("PARENT_PERMISSION_NOT_FOUND", 20009),
        Map.entry("PARENT_PERMISSION_NOT_TOP_LEVEL", 20010),
        Map.entry("SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED", 20011),
        Map.entry("RESOURCE_CODE_REQUIRED", 20012),
        Map.entry("CHILD_PERMISSION_NOT_FOUND", 20013),
        Map.entry("PERMISSION_NOT_CHILD", 20014),
        Map.entry("PERM_USER_NOT_FOUND", 20015),
        Map.entry("PERM_USER_ALREADY_EXISTS", 20016),
        Map.entry("DOMAIN_NOT_FOUND", 20017),
        Map.entry("DOMAIN_CONFIG_NOT_FOUND", 20018),
        Map.entry("DEPENDENCY_NOT_FOUND", 20019),
        Map.entry("CONFLICT_RULE_NOT_FOUND", 20020),
        Map.entry("TYPE_CODE_NOT_FOUND", 20021),
        Map.entry("ROLE_TYPE_MISMATCH", 20022),
        Map.entry("REQUEST_ITEMS_EMPTY", 20023),
        Map.entry("SYNC_RESOURCE_NOT_FOUND", 20024),
        Map.entry("RESOURCE_STATE_CONFLICT", 20025),
        Map.entry("SYSTEM_INIT_FAILED", 20026),
        Map.entry("VALIDATION_FAILED", 20027),
        Map.entry("TYPE_DEFINITION_NOT_FOUND", 20028),
        Map.entry("USER_ROLE_RELATION_NOT_FOUND", 20029),
        Map.entry("SYNC_TARGET_STATUS_INVALID", 20030),
        Map.entry("CONDITION_RULES_INVALID", 20031),
        Map.entry("CONFLICT_RULE_DUPLICATE", 20032),
        Map.entry("DIRECT_PERMISSION_CONFLICT", 20033),
        Map.entry("AUTO_DEP_READONLY", 20034),
        Map.entry("PERMISSION_NOT_FOUND", 20036),
        Map.entry("GRANT_CANNOT_DELEGATE", 20040),
        Map.entry("CONDITIONAL_PERMISSION_CANNOT_DELEGATE", 20041)
    );

    /**
     * 同名异号碰撞三组裁决表（T-ACCESS-038，2026-09-13 codex sol 复评补入的既有定案 +
     * 用户拍板段前缀消解）：六常量编号对与各自调用方语义原样保留。基线覆盖其中 5 项
     * 预归并码（10001/10002/10008/20015/20016）；PERM_INVALID_PARAM(20044) 为归并后
     * 新增码、不在归并前基线内，由本表专项锁定。
     */
    private static final Map<String, Integer> COLLISION_PAIR_LOCK = Map.of(
        "ADMIN_USER_NOT_FOUND", 10001,
        "PERM_USER_NOT_FOUND", 20015,
        "ADMIN_USER_ALREADY_EXISTS", 10002,
        "PERM_USER_ALREADY_EXISTS", 20016,
        "ADMIN_INVALID_PARAM", 10008,
        "PERM_INVALID_PARAM", 20044);

    private static final int ADMIN_SEGMENT_MIN = 10001;
    private static final int ADMIN_SEGMENT_MAX = 19999;
    private static final int PERM_SEGMENT_MIN = 20001;
    private static final int PERM_SEGMENT_MAX = 29999;
    private static final int GLOBAL_SEGMENT_MIN = 90001;
    private static final int GLOBAL_SEGMENT_MAX = 99999;
    private static final int FORBIDDEN_BUSINESS_RANGE_MIN = 40000;
    private static final int FORBIDDEN_BUSINESS_RANGE_MAX = 49999;

    private static Map<String, Integer> accessByName() {
        return java.util.Arrays.stream(AccessErrorCode.values())
            .collect(Collectors.toMap(Enum::name, AccessErrorCode::getCode, (a, b) -> a, TreeMap::new));
    }

    private static Map<String, Integer> globalByName() {
        return java.util.Arrays.stream(GlobalErrorCode.values())
            .collect(Collectors.toMap(Enum::name, GlobalErrorCode::code, (a, b) -> a, TreeMap::new));
    }

    @Test
    @DisplayName("基线保持：归并前基线逐条保持——码值与合并册常量名绑定不变（名称在、码值同、无重载；三组碰撞前缀名锁定编号对）")
    void preMergeBaseline_preserved() {
        Map<String, Integer> current = accessByName();
        Map<Integer, String> currentByCode = current.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Integer> e : PRE_MERGE_BASELINE.entrySet()) {
            String name = e.getKey();
            int code = e.getValue();
            boolean inBusinessSegment = (code >= ADMIN_SEGMENT_MIN && code <= ADMIN_SEGMENT_MAX)
                || (code >= PERM_SEGMENT_MIN && code <= PERM_SEGMENT_MAX);
            if (!inBusinessSegment) {
                continue;
            }
            if (RETIRED_ADMIN_NAMES.contains(name)) {
                if (currentByCode.containsKey(code)) {
                    violations.add("退役码值被复用: " + code + " -> " + currentByCode.get(code));
                }
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
        assertThat(violations).as("归并前基线必须逐码保持（合类不合号；条目数以 PRE_MERGE_BASELINE 断言为准）").isEmpty();
    }

    @Test
    @DisplayName("退役登记：归并后新增码 ROLE_API_RETIRED(10111) 已删除且码值不复用")
    void postMergeRetiredAdminCodes_removedAndNotReused() {
        Map<String, Integer> current = accessByName();
        Map<Integer, String> currentByCode = current.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));

        List<String> violations = new ArrayList<>();
        RETIRED_POST_MERGE_ADMIN.forEach((name, code) -> {
            if (current.containsKey(name)) {
                violations.add("退役枚举名仍存在: " + name);
            }
            if (currentByCode.containsKey(code)) {
                violations.add("退役码值被复用: " + code + " -> " + currentByCode.get(code));
            }
        });
        assertThat(violations).as("归并后新增退役码不得残留枚举名或复用码值").isEmpty();
    }

    @Test
    @DisplayName("基线保持：公共 GlobalErrorCode 仍为 90001/99999 两项")
    void globalErrorCode_unchanged() {
        assertThat(globalByName()).containsOnly(
            Map.entry("VALIDATION_FAILED", 90001),
            Map.entry("SYSTEM_ERROR", 99999));
    }

    @Test
    @DisplayName("分段归属（编号段判据）：合并册码值均在 1xxxx/2xxxx 业务段内、公共枚举仅在 9xxxx")
    void segments_codesWithinTheirNumberRanges() {
        List<String> violations = new ArrayList<>();

        accessByName().forEach((name, code) -> {
            boolean inAdminSegment = code >= ADMIN_SEGMENT_MIN && code <= ADMIN_SEGMENT_MAX;
            boolean inPermSegment = code >= PERM_SEGMENT_MIN && code <= PERM_SEGMENT_MAX;
            if (!inAdminSegment && !inPermSegment) {
                violations.add("AccessErrorCode." + name + "=" + code + " 越出 1xxxx/2xxxx 业务段");
            }
        });
        globalByName().forEach((name, code) -> {
            if (code < GLOBAL_SEGMENT_MIN || code > GLOBAL_SEGMENT_MAX) {
                violations.add("GlobalErrorCode." + name + "=" + code + " 越出 9xxxx 段");
            }
        });
        assertThat(violations).as("业务码只落业务段、公共码只落公共段").isEmpty();
    }

    @Test
    @DisplayName("无 4xxxx：合并册与公共枚举均不得定义 40000-49999 段业务错误码")
    void noBusinessErrorCodesIn4xxxxRange() {
        Map<String, Integer> all = new HashMap<>();
        all.putAll(accessByName());
        all.putAll(globalByName());

        List<String> violations = all.entrySet().stream()
            .filter(e -> e.getValue() >= FORBIDDEN_BUSINESS_RANGE_MIN && e.getValue() <= FORBIDDEN_BUSINESS_RANGE_MAX)
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.toList());
        assertThat(violations).as("不存在 4xxxx 业务错误码").isEmpty();
    }

    @Test
    @DisplayName("碰撞编号对：三组同名异号常量的段前缀名与码值一一对应（编号对与语义原样保留）")
    void collisionPairs_codePairsPreserved() {
        Map<String, Integer> current = accessByName();
        List<String> violations = new ArrayList<>();
        COLLISION_PAIR_LOCK.forEach((name, boxed) -> {
            int code = boxed;
            Integer currentCode = current.get(name);
            if (currentCode == null) {
                violations.add("碰撞常量缺失: " + name + "(" + code + ")");
            } else if (currentCode != code) {
                violations.add("碰撞编号对漂移: " + name + " " + code + " -> " + currentCode);
            }
        });
        assertThat(violations).as("三组碰撞编号对（10001/20015、10002/20016、10008/20044）必须原样保留").isEmpty();
    }

    @Test
    @DisplayName("无重复：合并册内及与公共枚举间码值全局唯一")
    void noDuplicateCodes() {
        Map<Integer, List<String>> owners = new TreeMap<>();
        accessByName().forEach((name, code) ->
            owners.computeIfAbsent(code, k -> new ArrayList<>()).add("AccessErrorCode." + name));
        globalByName().forEach((name, code) ->
            owners.computeIfAbsent(code, k -> new ArrayList<>()).add("GlobalErrorCode." + name));

        List<String> duplicates = owners.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> e.getKey() + " -> " + e.getValue())
            .collect(Collectors.toList());
        assertThat(duplicates).as("同一码值不得由多个错误码常量承载").isEmpty();
    }
}
