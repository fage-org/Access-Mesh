package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingUpdateReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.role.dto.req.RoleUpdateReq;
import cn.ac.fage.accessmesh.access.type.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserUpdateReq;
import cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateReq;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 显式清空协议统一校验锁（T-API-004，U006 四项拍板的协议面回归）。
 * <p>
 * 拍板口径（2026-09-24）：①新值与 Clear 同传全端点拒绝（含 role/resource 既有两域——
 * 取代旧「Clear 优先于 extra」的静默丢值口径）；②目标字段空串一律拒绝（清空唯一通道
 * =xxxClear，杜绝空串入库与唯一索引空串撞车）；③false/缺省无清空作用（null=不修改维持）；
 * ④TypeUpdateReq.extraClear 不支持清空语义（服务层拒任何非 null 值，DTO 层不做冲突锁——
 * extraClear 单独出现合法交给服务层给出专门错误信息）。
 * 本用例在旧实现（无 Clear 字段/冲突静默丢值）下不编译或必红。
 * </p>
 */
class ClearFieldProtocolValidationTest {

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

    private final Validator validator = validatorFactory.getValidator();

    // ---- 冲突拒绝（拍板①：全端点） ----

    @Test
    @DisplayName("值与 Clear 同传拒绝——user phone/email")
    void userConflictMustBeRejected() {
        assertFalse(validator.validate(new UserUpdateReq(1L, null, "13800000000", null, null, true, null)).isEmpty());
        assertFalse(validator.validate(new UserUpdateReq(1L, null, null, "a@b.c", null, null, true)).isEmpty());
    }

    @Test
    @DisplayName("值与 Clear 同传拒绝——type description / service 三字段 / mapping extra / abstract-user extra")
    void conflictMustBeRejectedAcrossTargets() {
        assertFalse(validator.validate(new TypeUpdateReq(1L, null, "描述", null, null, true, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", "/api", null, null, null, true, null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", null, "描述", null, null, null, true, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", null, null, null, "{\"k\":1}", null, null, true)).isEmpty());
        assertFalse(validator.validate(new ApiMappingUpdateReq(1L, 2L, null, null, null, null, "{\"k\":1}", true)).isEmpty());
        assertFalse(validator.validate(new AbstractUserUpdateReq(1L, null, null, "{\"k\":1}", true)).isEmpty());
    }

    @Test
    @DisplayName("值与 Clear 同传拒绝——role/resource 既有两域同批对齐（旧「Clear 优先」口径退役）")
    void legacyRoleResourceConflictMustBeRejected() {
        assertFalse(validator.validate(new RoleUpdateReq(1L, null, null, null, "{\"k\":1}", true)).isEmpty());
        assertFalse(validator.validate(new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq(
            "API", "code", null, null, null, null, "{\"k\":1}", true)).isEmpty());
    }

    // ---- 空串拒绝（拍板②：六字段，清空唯一通道=xxxClear） ----

    @Test
    @DisplayName("空串/纯空白拒绝——phone/email/description/basePath/extra")
    void blankMustBeRejected() {
        assertFalse(validator.validate(new UserUpdateReq(1L, null, "", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new UserUpdateReq(1L, null, " ", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new UserUpdateReq(1L, null, null, "", null, null, null)).isEmpty());
        assertFalse(validator.validate(new TypeUpdateReq(1L, null, "", null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new TypeUpdateReq(1L, null, null, null, " ", null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", "", null, null, null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", null, " ", null, null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new ServiceConfigReq("svc", "名", null, null, null, "", null, null, null)).isEmpty());
        assertFalse(validator.validate(new ApiMappingUpdateReq(1L, 2L, null, null, null, null, " ", null)).isEmpty());
    }

    // ---- false/缺省无清空作用；单用形态放行（拍板③；既有正常 extraClear 消费者不回退） ----

    @Test
    @DisplayName("Clear 单用放行、false 与值同传放行、非目标字段不受影响")
    void singleUseAndFalseMustPass() {
        assertTrue(validator.validate(new UserUpdateReq(1L, null, null, null, null, true, null)).isEmpty());
        assertTrue(validator.validate(new UserUpdateReq(1L, "名", "13800000000", "a@b.c", null, false, false)).isEmpty());
        assertTrue(validator.validate(new TypeUpdateReq(1L, "名", null, null, null, true, null)).isEmpty());
        assertTrue(validator.validate(new ServiceConfigReq("svc", "名", null, null, null, null, true, true, true)).isEmpty());
        assertTrue(validator.validate(new ApiMappingUpdateReq(1L, 2L, null, null, null, null, null, true)).isEmpty());
        assertTrue(validator.validate(new RoleUpdateReq(1L, null, null, null, null, true)).isEmpty());
        assertTrue(validator.validate(new RoleUpdateReq(1L, "名", 1, 0, "{\"k\":1}", false)).isEmpty());
        // abstract-user：extraClear 单用=合法业务载荷放行（空 patch 计入）；与值同传见冲突用例
        assertTrue(validator.validate(new AbstractUserUpdateReq(1L, null, null, null, true)).isEmpty());
        // false 与空串同传：冲突锁不触发，空串锁独立生效（两锁正交）
        assertFalse(validator.validate(new UserUpdateReq(1L, null, "", null, null, false, null)).isEmpty());
        // abstract-user extra 空白拒绝（协议覆盖字段）
        assertFalse(validator.validate(new AbstractUserUpdateReq(1L, null, null, " ", null)).isEmpty());
    }

    @Test
    @DisplayName("type extraClear 单独出现放行（语义拒绝在服务层给出专门错误信息，非 Bean Validation）")
    void typeExtraClearDelegatesToServiceLayer() {
        assertTrue(validator.validate(new TypeUpdateReq(1L, null, null, null, null, null, true)).isEmpty());
    }
}
