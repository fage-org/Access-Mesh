package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LocalProjectionGuardTest {

    private final LocalProjectionGuard guard = new LocalProjectionGuard();

    @Test
    @DisplayName("owner=access-service 时拒绝")
    void rejectIfLocalOwner() {
        assertThatThrownBy(() -> guard.rejectIfLocalOwner(LocalProjectionOwner.SERVICE_CODE))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    @DisplayName("外部 owner 放行")
    void allowExternalOwner() {
        assertDoesNotThrow(() -> guard.rejectIfLocalOwner(null));
        assertDoesNotThrow(() -> guard.rejectIfLocalOwner("hr-service"));
    }

    @Test
    @DisplayName("保留业务键 LOCAL_USER / ORG / USER|ORG|MENU（管理入口清单）/ SYS_USER_ORG 拒绝")
    void rejectReservedKeys() {
        assertThatThrownBy(() -> guard.rejectReservedSubjectType("LOCAL_USER"))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.rejectReservedRoleType("ORG"))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.rejectReservedRoleType("POSITION"))
            .isInstanceOf(BizException.class);
        // 管理入口类型保留清单 {USER, ORG, MENU, ROLE}（T-ACCESS-018 换值、T-ACCESS-019 增补 ROLE；
        // 仅人工建资源入口使用，外部 sync 不再调用本清单——本地投影行改按所有权保护）
        assertThatThrownBy(() -> guard.rejectReservedResourceType("USER"))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.rejectReservedResourceType("ORG"))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.rejectReservedResourceType("MENU"))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.rejectReservedResourceType("ROLE"))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.rejectReservedUserRoleSource("SYS_USER_ORG"))
            .isInstanceOf(BizException.class);
        assertDoesNotThrow(() -> guard.rejectReservedSubjectType("USER"));
        assertDoesNotThrow(() -> guard.rejectReservedSubjectType("ADMIN_USER"));
        assertDoesNotThrow(() -> guard.rejectReservedRoleType("BASIC_ROLE"));
        assertDoesNotThrow(() -> guard.rejectReservedResourceType("ADMIN_DICT"));
    }

    @Test
    @DisplayName("内部 sourceService 拒绝")
    void rejectInternalSource() {
        assertThatThrownBy(() -> guard.rejectInternalSourceService("access-service"))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> guard.rejectInternalSourceService("admin-service"))
            .isInstanceOf(BizException.class);
        assertDoesNotThrow(() -> guard.rejectInternalSourceService("example-service"));
    }

    @Test
    @DisplayName("本地投影用户实体拒绝")
    void rejectLocalUserEntity() {
        AbstractUser user = new AbstractUser();
        user.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        assertThatThrownBy(() -> guard.rejectIfLocalUser(user))
            .isInstanceOf(BizException.class);
    }
}
