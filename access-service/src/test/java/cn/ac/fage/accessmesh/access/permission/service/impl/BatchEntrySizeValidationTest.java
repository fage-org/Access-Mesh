package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.IdsReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeysReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceBatchCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引擎批量入口 @Size(max=1000) Bean Validation 层锁定（codex 五轮外评 P2）。
 * <p>
 * 无上限列表的放大面分三类，全部须在 HTTP 层 @Valid 拒 400 而非进引擎：
 * <ul>
 *   <li>送进判定面闭包 CTE（UserAssignRoleReq/UserRoleBatchRevokeReq 角色集 →
 *       getDeniedResourceCodes；ResourceKeysReq/IdsReq → getDenied\* 直连）；</li>
 *   <li>逐项执行完整引擎管线（BatchAuthCheckReq.items，perm-common 单源——T-PERM-065 收敛后服务端与 SDK 共用一类）；</li>
 *   <li>内存网格笛卡尔组装（QueryScopesReq 三列表）。</li>
 * </ul>
 * 旧实现（仅 @NotEmpty）下 1001 条用例失败。
 * </p>
 */
class BatchEntrySizeValidationTest {

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

    @Test
    void assignRoleItemsMustBeCappedAt1000() {
        Validator validator = validatorFactory.getValidator();
        assertTrue(validator.validate(new UserAssignRoleReq(items(1000,
            i -> new UserAssignRoleReq.AssignItem("LOCAL_USER", "u" + i, null, "BASIC_ROLE", "r1", null, null, null))))
            .isEmpty());
        assertFalse(validator.validate(new UserAssignRoleReq(items(1001,
            i -> new UserAssignRoleReq.AssignItem("LOCAL_USER", "u" + i, null, "BASIC_ROLE", "r1", null, null, null))))
            .isEmpty(), "角色分配集进 getDeniedResourceCodes 闭包 CTE，超限须 400");
    }

    @Test
    void batchRevokeItemsMustBeCappedAt1000() {
        Validator validator = validatorFactory.getValidator();
        assertTrue(validator.validate(new UserRoleBatchRevokeReq(items(1000,
            i -> new UserRoleBatchRevokeReq.RevokeItem("LOCAL_USER", "u" + i, null, "BASIC_ROLE", "r1", null))))
            .isEmpty());
        assertFalse(validator.validate(new UserRoleBatchRevokeReq(items(1001,
            i -> new UserRoleBatchRevokeReq.RevokeItem("LOCAL_USER", "u" + i, null, "BASIC_ROLE", "r1", null))))
            .isEmpty(), "角色撤销集进 getDeniedResourceCodes 闭包 CTE，超限须 400");
    }

    @Test
    void batchAuthCheckItemsMustBeCappedAt1000() {
        Validator validator = validatorFactory.getValidator();
        BatchAuthCheckReq over = new BatchAuthCheckReq("LOCAL_USER", "1",
            items(1001, i -> new BatchAuthCheckReq.AuthCheckItem("MENU", null, "VIEW", null, null, null)), null, null, null, null, null);
        assertFalse(validator.validate(over).isEmpty(), "逐项执行完整引擎管线，超限须 400");
        BatchAuthCheckReq exact = new BatchAuthCheckReq("LOCAL_USER", "1",
            items(1000, i -> new BatchAuthCheckReq.AuthCheckItem("MENU", null, "VIEW", null, null, null)), null, null, null, null, null);
        assertTrue(validator.validate(exact).isEmpty());
    }

    @Test
    void parentContextPairingMustBeValidated() {
        // T-PERM-058：parentResourceTypeCode 与 parentResourceCode 必须成对——半传 400
        // （Bean Validation 生效性锁：校验方法是 isXxx getter，非 public 不被 HV 拾取，双轨评审 P2-2）
        Validator validator = validatorFactory.getValidator();

        // 完整父上下文（type+code+operations）→ 约束通过
        AuthCheckReq paired = new AuthCheckReq("USER", "u-1", "MENU", "m-1", "VIEW",
            null, null, null, "REPORT", "report:1", null, List.of("VIEW"), null);
        assertTrue(validator.validate(paired).isEmpty(), "成对+操作集非空合法");

        // 给了父资源但 operations 缺省/空集 → 400（P1-1 定案：必填口径，引擎对空集不发父判定查询）
        AuthCheckReq noOps = new AuthCheckReq("USER", "u-1", "MENU", "m-1", "VIEW",
            null, null, null, "REPORT", "report:1", null, null, null);
        assertFalse(validator.validate(noOps).isEmpty(), "父上下文缺操作集须 400");
        AuthCheckReq emptyOps = new AuthCheckReq("USER", "u-1", "MENU", "m-1", "VIEW",
            null, null, null, "REPORT", "report:1", null, List.of(), null);
        assertFalse(validator.validate(emptyOps).isEmpty(), "父上下文空操作集须 400");

        // claude 外评 P2-2：操作集上限与 query-scopes 同名口径对齐（逐元素进 SQL IN 绑定）
        AuthCheckReq overOps = new AuthCheckReq("USER", "u-1", "MENU", "m-1", "VIEW",
            null, null, null, "REPORT", "report:1", null,
            java.util.Collections.nCopies(1001, "VIEW"), null);
        assertFalse(validator.validate(overOps).isEmpty(), "父上下文操作集超 1000 须 400");

        // 半传：只给 type 不给 code
        AuthCheckReq halfType = new AuthCheckReq("USER", "u-1", "MENU", "m-1", "VIEW",
            null, null, null, "REPORT", null, null, null, null);
        assertFalse(validator.validate(halfType).isEmpty(), "半传 type 须 400");

        // 半传：只给 code 不给 type
        AuthCheckReq halfCode = new AuthCheckReq("USER", "u-1", "MENU", "m-1", "VIEW",
            null, null, null, null, "report:1", null, null, null);
        assertFalse(validator.validate(halfCode).isEmpty(), "半传 code 须 400");

        // batch-check 请求级父上下文同款成对约束
        BatchAuthCheckReq batchHalf = new BatchAuthCheckReq("USER", "u-1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("MENU", "m-1", "VIEW", null, null, null)),
            "REPORT", null, null, null, null);
        assertFalse(validator.validate(batchHalf).isEmpty(), "batch-check 请求级父上下文半传同款 400");
    }

    @Test
    void batchAuthCheckItemFieldsMustCascadeValidate() {
        // codex 外评存量观察（2026-09-11）：items 缺 @Valid 时嵌套 @NotBlank 不级联，
        // 空白 resourceTypeCode/operationCode 穿透到引擎走 fail-closed deny 而非 400。
        // 旧实现（无 @Valid）下本用例失败。
        Validator validator = validatorFactory.getValidator();

        // 空白 resourceTypeCode → 须 400
        BatchAuthCheckReq blankType = new BatchAuthCheckReq("LOCAL_USER", "1",
            List.of(new BatchAuthCheckReq.AuthCheckItem(" ", "m-1", "VIEW", null, null, null)), null, null, null, null, null);
        assertFalse(validator.validate(blankType).isEmpty(), "嵌套空白 resourceTypeCode 须级联拒 400");

        // 空白 operationCode → 须 400
        BatchAuthCheckReq blankOp = new BatchAuthCheckReq("LOCAL_USER", "1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("MENU", "m-1", "", null, null, null)), null, null, null, null, null);
        assertFalse(validator.validate(blankOp).isEmpty(), "嵌套空白 operationCode 须级联拒 400");

        // null 元素 → 须 400（List 元素级 @NotNull）
        BatchAuthCheckReq nullElement = new BatchAuthCheckReq("LOCAL_USER", "1",
            java.util.Arrays.asList((BatchAuthCheckReq.AuthCheckItem) null), null, null, null, null, null);
        assertFalse(validator.validate(nullElement).isEmpty(), "items null 元素须 400");

        // 合法 item → 无违例
        BatchAuthCheckReq valid = new BatchAuthCheckReq("LOCAL_USER", "1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("MENU", "m-1", "VIEW", null, null, null)), null, null, null, null, null);
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void queryScopesListsMustBeCappedAt1000() {
        Validator validator = validatorFactory.getValidator();
        QueryScopesReq base = new QueryScopesReq("LOCAL_USER", "1", "REPORT", "r-1", null,
            codes(1000), codes(1000), codes(1000), null, null, null);
        assertTrue(validator.validate(base).isEmpty());

        assertFalse(validator.validate(new QueryScopesReq("LOCAL_USER", "1", "REPORT", "r-1", null,
            codes(1001), codes(1000), codes(1000), null, null, null)).isEmpty(),
            "parentOperationCodes 超限须 400");
        assertFalse(validator.validate(new QueryScopesReq("LOCAL_USER", "1", "REPORT", "r-1", null,
            codes(1000), codes(1001), codes(1000), null, null, null)).isEmpty(),
            "scopeResourceTypeCodes 超限须 400");
        assertFalse(validator.validate(new QueryScopesReq("LOCAL_USER", "1", "REPORT", "r-1", null,
            codes(1000), codes(1000), codes(1001), null, null, null)).isEmpty(),
            "scopeOperationCodes 超限须 400");
    }

    @Test
    void getDeniedDirectEntryKeysAndIdsMustBeCappedAt1000() {
        Validator validator = validatorFactory.getValidator();
        // codex 四轮修复（@Size 补齐）当时未配校验层锁，此处一并锁定
        assertTrue(validator.validate(new ResourceKeysReq(items(1000,
            i -> new ResourceKeyReq("MENU", "m" + i, null)))).isEmpty());
        assertFalse(validator.validate(new ResourceKeysReq(items(1001,
            i -> new ResourceKeyReq("MENU", "m" + i, null)))).isEmpty());

        assertTrue(validator.validate(new IdsReq(IntStream.rangeClosed(1, 1000)
            .mapToObj(Long::valueOf).toList())).isEmpty());
        assertFalse(validator.validate(new IdsReq(IntStream.rangeClosed(1, 1001)
            .mapToObj(Long::valueOf).toList())).isEmpty());
    }

    @Test
    void itemsNullElementMustBeRejectedAt400() {
        // claude 外评（2026-09-12）：items 无元素级 @NotNull 时 null 元素穿透到服务端 stream 解引用 NPE 500
        // （ResourceBatchCreateReq 的 map(ResourceCreateReq::resourceTypeCode) 对 null 元素在 filter 前即炸）。
        // 元素级 @NotNull 只拦 null 本身、不级联嵌套字段校验（不触碰 batch-create 宽容收集拍板语义）。
        Validator validator = validatorFactory.getValidator();
        assertFalse(validator.validate(new ResourceBatchCreateReq(
            java.util.Arrays.asList((ResourceCreateReq) null))).isEmpty(),
            "batch-create items null 元素须 400");
        assertFalse(validator.validate(new UserAssignRoleReq(
            java.util.Arrays.asList((UserAssignRoleReq.AssignItem) null))).isEmpty(),
            "assign items null 元素须 400");
        assertFalse(validator.validate(new UserRoleBatchRevokeReq(
            java.util.Arrays.asList((UserRoleBatchRevokeReq.RevokeItem) null))).isEmpty(),
            "revoke items null 元素须 400");
    }

    private static <T> List<T> items(int count, java.util.function.IntFunction<T> factory) {
        return IntStream.range(0, count).mapToObj(factory).toList();
    }

    private static List<String> codes(int count) {
        return IntStream.range(0, count).mapToObj(i -> "OP" + i).toList();
    }
}
