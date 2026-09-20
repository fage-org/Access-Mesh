package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.grant.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationCreateReq;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationKeysReq;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.type.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.type.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * operationCodeKey 族大小写口径 Bean Validation 层锁定（T-PERM-066 raw 严格化）。
 * <p>
 * 旧实现（无 @Pattern + 授权域 toUpperCase/trim 归一）下小写用例全部通过校验——
 * 授权面（apply-grant-plan 经归一）静默成功、查询面（check/dependency 20005）拒绝，
 * 同一入参双语义。本测试锁「小写/含空格码在 DTO 边界 400（90001）」：覆盖 SDK 单源
 * 5 Req 与 access-service 定义/依赖/授权面，@Pattern 注解任一被删即红。
 * </p>
 */
class OperationCodeCaseValidationTest {

    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private static Validator validator() {
        return validatorFactory.getValidator();
    }

    @Test
    @DisplayName("auth/check：operationCode/resourceTypeCode/parent 链小写拒绝，大写放行")
    void authCheckRejectsLowercaseCodes() {
        assertTrue(validator().validate(new AuthCheckReq("LOCAL_USER", "u1", "DATA", "d:1",
            "VIEW", null, null, null, "REPORT", "r:1", null, List.of("VIEW"), null)).isEmpty());
        assertFalse(validator().validate(new AuthCheckReq("LOCAL_USER", "u1", "DATA", "d:1",
            "view", null, null, null, null, null, null, null, null)).isEmpty(),
            "小写操作码须在 DTO 边界拒绝（旧实现归一后静默成功）");
        assertFalse(validator().validate(new AuthCheckReq("LOCAL_USER", "u1", "data", "d:1",
            "VIEW", null, null, null, null, null, null, null, null)).isEmpty(),
            "小写资源类型码须拒绝");
        assertFalse(validator().validate(new AuthCheckReq("LOCAL_USER", "u1", "DATA", "d:1",
            "VIEW", null, null, null, "report", "r:1", null, List.of("VIEW"), null)).isEmpty(),
            "小写父资源类型码须拒绝");
        assertFalse(validator().validate(new AuthCheckReq("LOCAL_USER", "u1", "DATA", "d:1",
            "VIEW", null, null, null, "REPORT", "r:1", null, List.of(" view "), null)).isEmpty(),
            "父操作码元素小写/含空格须拒绝（旧实现 trim+归一后静默匹配）");
    }

    @Test
    @DisplayName("auth/batch-check：item 与请求级 parent 链小写拒绝")
    void batchAuthCheckRejectsLowercaseCodes() {
        BatchAuthCheckReq valid = new BatchAuthCheckReq("LOCAL_USER", "u1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("DATA", "d:1", "VIEW", null, null, null)),
            "REPORT", "r:1", null, List.of("VIEW"), null);
        assertTrue(validator().validate(valid).isEmpty());
        assertFalse(validator().validate(new BatchAuthCheckReq("LOCAL_USER", "u1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("DATA", "d:1", "view", null, null, null)),
            null, null, null, null, null)).isEmpty(),
            "item 小写操作码须拒绝");
        assertFalse(validator().validate(new BatchAuthCheckReq("LOCAL_USER", "u1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("data", "d:1", "VIEW", null, null, null)),
            null, null, null, null, null)).isEmpty(),
            "item 小写资源类型码须拒绝");
        assertFalse(validator().validate(new BatchAuthCheckReq("LOCAL_USER", "u1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("DATA", "d:1", "VIEW", null, null, null)),
            "REPORT", "r:1", null, List.of("View"), null)).isEmpty(),
            "请求级父操作码元素小写须拒绝");
    }

    @Test
    @DisplayName("auth/query-scopes：parent/scope 四列表元素小写拒绝")
    void queryScopesRejectsLowercaseCodes() {
        QueryScopesReq valid = new QueryScopesReq("LOCAL_USER", "u1", "REPORT", "r:1", null,
            List.of("VIEW"), List.of("DATA"), List.of("DATA_READ"), null, null, null);
        assertTrue(validator().validate(valid).isEmpty());
        assertFalse(validator().validate(new QueryScopesReq("LOCAL_USER", "u1", "report", "r:1", null,
            List.of("VIEW"), List.of("DATA"), List.of("DATA_READ"), null, null, null)).isEmpty(),
            "小写父资源类型码须拒绝");
        assertFalse(validator().validate(new QueryScopesReq("LOCAL_USER", "u1", "REPORT", "r:1", null,
            List.of("view"), List.of("DATA"), List.of("DATA_READ"), null, null, null)).isEmpty(),
            "父操作码元素小写须拒绝");
        assertFalse(validator().validate(new QueryScopesReq("LOCAL_USER", "u1", "REPORT", "r:1", null,
            List.of("VIEW"), List.of("data"), List.of("DATA_READ"), null, null, null)).isEmpty(),
            "范围资源类型码元素小写须拒绝");
        assertFalse(validator().validate(new QueryScopesReq("LOCAL_USER", "u1", "REPORT", "r:1", null,
            List.of("VIEW"), List.of("DATA"), List.of("data_read"), null, null, null)).isEmpty(),
            "范围操作码元素小写须拒绝");
    }

    @Test
    @DisplayName("auth/query-resources 与 effective-permission-codes：两列表元素小写拒绝")
    void queryResourcesAndEffectiveCodesRejectLowercase() {
        assertTrue(validator().validate(new QueryResourcesReq("LOCAL_USER", "u1",
            List.of("DATA"), List.of("VIEW"), null, null, null, null, null)).isEmpty());
        assertFalse(validator().validate(new QueryResourcesReq("LOCAL_USER", "u1",
            List.of("data"), List.of("VIEW"), null, null, null, null, null)).isEmpty(),
            "资源类型码元素小写须拒绝");
        assertFalse(validator().validate(new QueryResourcesReq("LOCAL_USER", "u1",
            List.of("DATA"), List.of("view"), null, null, null, null, null)).isEmpty(),
            "操作码元素小写须拒绝");
        assertFalse(validator().validate(new UserEffectivePermissionCodesReq("LOCAL_USER", "u1",
            List.of("user"))).isEmpty(),
            "聚合白名单资源类型码元素小写须拒绝");
    }

    @Test
    @DisplayName("apply-grant-plan：GrantRecordKey 主键与 children 嵌套级联小写拒绝")
    void applyGrantPlanRejectsLowercaseKeys() {
        ApplyGrantPlanReq.GrantRecordKey validKey = new ApplyGrantPlanReq.GrantRecordKey(
            "DATA", "d:1", null, "VIEW", ScopeMode.INSTANCE, null, null, false);
        assertTrue(validator().validate(validKey).isEmpty());
        assertFalse(validator().validate(new ApplyGrantPlanReq.GrantRecordKey(
            "DATA", "d:1", null, "view", ScopeMode.INSTANCE, null, null, false)).isEmpty(),
            "小写操作码须拒绝（旧实现归一后授权成功）");
        assertFalse(validator().validate(new ApplyGrantPlanReq.GrantRecordKey(
            "data", "d:1", null, "VIEW", ScopeMode.INSTANCE, null, null, false)).isEmpty(),
            "小写资源类型码须拒绝");
        // 根对象级联：children 内小写键须随 @Valid 链拒绝（嵌套 List 级联先例 ApplyGrantPlanReq）
        ApplyGrantPlanReq root = new ApplyGrantPlanReq(null, "BASIC_ROLE", "r1",
            new ApplyGrantPlanReq.GrantPlan(
                List.of(new ApplyGrantPlanReq.CreateItem(validKey, null,
                    List.of(new ApplyGrantPlanReq.GrantRecordKey(
                        "DATA", "d:2", null, "view", ScopeMode.INSTANCE, null, null, false)))),
                null, null));
        assertFalse(validator().validate(root).isEmpty(),
            "children 嵌套小写键须随 @Valid 级联拒绝");
    }

    @Test
    @DisplayName("MANIFEST 级联校验拒绝小写资源类型与源/目标操作码")
    void manifestRejectsLowercaseCodes() {
        for (String[] values : List.of(new String[]{"REPORT", "VIEW", "DATA", "READ"},
                new String[]{"report", "VIEW", "DATA", "READ"},
                new String[]{"REPORT", "view", "DATA", "READ"},
                new String[]{"REPORT", "VIEW", "data", "READ"},
                new String[]{"REPORT", "VIEW", "DATA", "read"})) {
            var request = new PermissionManifestReq(1, "1", "r1", List.of(
                    new PermissionManifestReq.Dependency("d1",
                            new PermissionManifestReq.ResourceKey(values[0], "report", null), List.of(values[1]),
                            List.of(new PermissionManifestReq.Requirement(
                                    new PermissionManifestReq.ResourceKey(values[2], "data", null), List.of(values[3]))), null)));
            boolean uppercase = java.util.Arrays.stream(values).allMatch(v -> v.equals(v.toUpperCase(java.util.Locale.ROOT)));
            org.junit.jupiter.api.Assertions.assertEquals(uppercase, validator().validate(request).isEmpty());
        }
    }

    @Test
    @DisplayName("操作/类型定义面：code 与 typeCode 小写拒绝（定义侧锁死）")
    void definitionReqRejectsLowercaseCodes() {
        assertTrue(validator().validate(new OperationCreateReq(
            "DATA", "DATA_EXPORT", "导出", 8L, null)).isEmpty());
        assertFalse(validator().validate(new OperationCreateReq(
            "DATA", "data_export", "导出", 8L, null)).isEmpty(),
            "小写操作码定义须拒绝（未部署零存量，定义面自此只进大写）");
        assertFalse(validator().validate(new OperationCreateReq(
            "data", "DATA_EXPORT", "导出", 8L, null)).isEmpty(),
            "小写资源类型码须拒绝");
        assertFalse(validator().validate(new OperationUpdateReq(
            "DATA", "view", null, null, null)).isEmpty(),
            "update 小写定位键须拒绝");
        assertFalse(validator().validate(new OperationKeysReq(List.of(
            new OperationKeyReq("DATA", "view")))).isEmpty(),
            "remove item 小写定位键须拒绝");
        assertFalse(validator().validate(new TypeCreateReq(
            "resource_type", "data", "数据", null, null, null, null, null)).isEmpty(),
            "小写 typeCode 定义须拒绝");
        assertFalse(validator().validate(new TypeCreateReq(
            "resource_type", "MY-TYPE", "数据", null, null, null, null, null)).isEmpty(),
            "中划线 typeCode 定义须拒绝（T-PERM-066 外评 grok P2：charset 收敛大写族）");
        assertTrue(validator().validate(new TypeCreateReq(
            "resource_type", null, "数据", null, null, null, null, null)).isEmpty(),
            "typeCode 可选缺省放行（服务端生成大写）");
        // 留空生成语义（grok P2）：@Pattern 只对 null 跳过、"" 参与匹配——正则须显式放行空串，
        // 否则 HTTP "typeCode": "" 在进服务层 isBlank() 生成分支前即被 400（旧实现下本用例必红）
        assertTrue(validator().validate(new TypeCreateReq(
            "resource_type", "", "数据", null, null, null, null, null)).isEmpty(),
            "typeCode 空串放行走留空生成分支（§12.1 留空按规则生成）");
    }
}
