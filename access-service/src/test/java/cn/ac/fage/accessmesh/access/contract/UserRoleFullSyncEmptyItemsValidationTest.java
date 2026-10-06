package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.sync.dto.UserRoleFullSyncReq;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * user-role/full-sync 空清单校准语义校验锁（2026-10-06 逐任务评审 P2）。
 * <p>
 * items 由 @NotEmpty 收敛为 @NotNull（对齐资源通道 ResourceEntityFullSyncReq 先例）：
 * 最后一名成员/角色删除后的 FULL 校准路径（scope 内全部解绑）此前被 @NotEmpty 挡死
 * 为必 400，手册指引的恢复路径不可执行。旧实现下「空清单无 items 违例」断言必红。
 * scope 为 null 的违例不在本锁范围（只断言 items 属性面）。
 * </p>
 */
class UserRoleFullSyncEmptyItemsValidationTest {

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

    private static boolean hasItemsViolation(Set<ConstraintViolation<UserRoleFullSyncReq>> violations) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("items"));
    }

    @Test
    @DisplayName("空清单合法：FULL 校准按 scope 全量解绑（旧 @NotEmpty 实现必红）")
    void emptyItemsAllowed() {
        Validator validator = validatorFactory.getValidator();
        Set<ConstraintViolation<UserRoleFullSyncReq>> violations =
            validator.validate(new UserRoleFullSyncReq(null, List.of()));
        assertFalse(hasItemsViolation(violations), "空 items 不得产生违例（校准语义）");
    }

    @Test
    @DisplayName("null 清单仍拒绝（@NotNull 兜底，校准须显式空数组）")
    void nullItemsStillRejected() {
        Validator validator = validatorFactory.getValidator();
        Set<ConstraintViolation<UserRoleFullSyncReq>> violations =
            validator.validate(new UserRoleFullSyncReq(null, null));
        assertTrue(hasItemsViolation(violations), "null items 必须拒绝（缺省语义歧义 fail-closed）");
    }
}
