package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.role.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleBatchRevokeReq;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户-角色三写入口 relationId @Positive Bean Validation 层锁定（T-ADMIN-030 外评处置）。
 * <p>
 * 0 与 null 在 {@code uk_user_role（COALESCE(relation_id,0)）}同槽位而内存三元键区分——
 * 0 入库会形成 null 侧分配/撤销全部 500 或查不到的持粘行，须在 HTTP 层 @Valid 拒 400
 * 而非进服务层。旧实现（无 @Positive）下 0 值用例通过校验。null（不指定关系）与正整数合法。
 * 经外层请求体校验（items 带 element::@Valid 级联），与控制器 @Valid 真实路径一致。
 * </p>
 */
class UserRoleRelationIdValidationTest {

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
    void assignItemMustRejectZeroAndNegativeRelationId() {
        Validator validator = validatorFactory.getValidator();
        assertFalse(validator.validate(assignReq(0L)).isEmpty(), "relationId=0 与 null 同 UK 槽位，必须拒绝");
        assertFalse(validator.validate(assignReq(-1L)).isEmpty());
        assertTrue(validator.validate(assignReq(null)).isEmpty(), "null=不指定关系，合法");
        assertTrue(validator.validate(assignReq(33L)).isEmpty());
    }

    @Test
    void batchAssignMustRejectZeroAndNegativeRelationId() {
        Validator validator = validatorFactory.getValidator();
        assertFalse(validator.validate(new UserRoleBatchAssignReq(
                List.of("u-1"), "USER", null, "BASIC_ROLE", "r-1", 0L)).isEmpty());
        assertFalse(validator.validate(new UserRoleBatchAssignReq(
                List.of("u-1"), "USER", null, "BASIC_ROLE", "r-1", -1L)).isEmpty());
        assertTrue(validator.validate(new UserRoleBatchAssignReq(
                List.of("u-1"), "USER", null, "BASIC_ROLE", "r-1", null)).isEmpty());
        assertTrue(validator.validate(new UserRoleBatchAssignReq(
                List.of("u-1"), "USER", null, "BASIC_ROLE", "r-1", 33L)).isEmpty());
    }

    @Test
    void batchRevokeMustRejectZeroAndNegativeRelationId() {
        Validator validator = validatorFactory.getValidator();
        assertFalse(validator.validate(revokeReq(0L)).isEmpty());
        assertFalse(validator.validate(revokeReq(-1L)).isEmpty());
        assertTrue(validator.validate(revokeReq(null)).isEmpty());
        assertTrue(validator.validate(revokeReq(33L)).isEmpty());
    }

    private static UserAssignRoleReq assignReq(Long relationId) {
        return new UserAssignRoleReq(List.of(new UserAssignRoleReq.AssignItem(
                "LOCAL_USER", "u-1", null, "BASIC_ROLE", "r-1", relationId, null, null)));
    }

    private static UserRoleBatchRevokeReq revokeReq(Long relationId) {
        return new UserRoleBatchRevokeReq(List.of(new UserRoleBatchRevokeReq.RevokeItem(
                "LOCAL_USER", "u-1", null, "BASIC_ROLE", "r-1", relationId)));
    }
}
