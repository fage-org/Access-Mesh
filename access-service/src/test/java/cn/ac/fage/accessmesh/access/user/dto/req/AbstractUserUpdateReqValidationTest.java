package cn.ac.fage.accessmesh.access.user.dto.req;

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
 * 空 patch 拒绝锁定（T-ACCESS-034）：AbstractUserUpdateReq 至少一个业务字段。
 * <p>
 * 旧实现（无校验）下空 patch 会无门禁落点地走完写库/投影/审计/缓存失效全链路
 * （updateUser 仅 userId、无任何业务字段时字段分档门禁两分支均不触发，成为
 * 无条件写副作用）。经 Bean Validation 在 Controller 层拒绝——@Valid 触发
 * MethodArgumentNotValidException，GlobalExceptionHandler 映射 90001（HTTP 400），
 * 不落服务层（禁 IllegalArgumentException——该通道返回 code=400 非 90001）。
 * </p>
 */
class AbstractUserUpdateReqValidationTest {

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
    @DisplayName("空 patch（仅 userId）拒绝——旧实现下本用例失败")
    void emptyPatchMustBeRejected() {
        Validator validator = validatorFactory.getValidator();
        assertFalse(validator.validate(new AbstractUserUpdateReq(1L, null, null, null, null)).isEmpty(),
            "仅 userId 无任何业务字段必须被 Bean Validation 拒绝");
    }

    @Test
    @DisplayName("任一业务字段单独出现即放行（name / enabled / extra 三分支）")
    void anySingleBusinessFieldMustPass() {
        Validator validator = validatorFactory.getValidator();
        assertTrue(validator.validate(new AbstractUserUpdateReq(1L, "新名", null, null, null)).isEmpty());
        assertTrue(validator.validate(new AbstractUserUpdateReq(1L, null, false, null, null)).isEmpty());
        assertTrue(validator.validate(new AbstractUserUpdateReq(1L, null, null, "{\"k\":1}", null)).isEmpty());
    }

    @Test
    @DisplayName("组合字段与全字段放行")
    void combinedFieldsMustPass() {
        Validator validator = validatorFactory.getValidator();
        assertTrue(validator.validate(new AbstractUserUpdateReq(1L, "新名", true, null, null)).isEmpty());
        assertTrue(validator.validate(new AbstractUserUpdateReq(1L, "新名", true, "{\"k\":1}", null)).isEmpty());
    }
}
