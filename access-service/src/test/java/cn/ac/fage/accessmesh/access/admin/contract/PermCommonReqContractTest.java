package cn.ac.fage.accessmesh.access.admin.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 契约一致性测试：
 * <p>
 * 验证 perm-common DTO 的校验注解与设计契约一致。
 * <p>
 * 背景：perm-common 和 permission-center 内部各自维护一份 DTO（EXT-10），
 * Phase 1 M1 放宽 domainCode 的 @NotBlank 时必须双端同改，否则请求被 400 拒绝。
 * 此测试在构建时自动发现注解回退，防止类似问题复发。
 * <p>
 * 由于 admin-service 无法直接引用 permission-center 内部 DTO，
 * 本测试通过断言 perm-common DTO 的「字段名 → 是否必填」映射来守卫契约，
 * permission-center 侧应有镜像测试。
 * <p>
 * 契约依据：user-role-proxy-fix-plan.md M1 + EXT-12
 */
class PermCommonReqContractTest {

    // ========== UserAssignRoleReq.AssignItem ==========

    @Nested
    @DisplayName("UserAssignRoleReq.AssignItem")
    class AssignItemContract {

        private final Class<?> recordClass =
            cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq.AssignItem.class;

        @Test
        @DisplayName("subjectTypeCode 必须 @NotBlank")
        void subjectTypeCode_isNotBlank() {
            assertThat(isNotBlank("subjectTypeCode")).isTrue();
        }

        @Test
        @DisplayName("subjectExternalId 必须 @NotBlank")
        void subjectExternalId_isNotBlank() {
            assertThat(isNotBlank("subjectExternalId")).isTrue();
        }

        @Test
        @DisplayName("domainCode 可为空（功能角色场景，M1 放宽）")
        void domainCode_isOptional() {
            assertThat(isNotBlank("domainCode")).isFalse();
            assertThat(isNotNull("domainCode")).isFalse();
        }

        @Test
        @DisplayName("roleTypeCode 必须 @NotBlank")
        void roleTypeCode_isNotBlank() {
            assertThat(isNotBlank("roleTypeCode")).isTrue();
        }

        @Test
        @DisplayName("roleExternalId 必须 @NotBlank")
        void roleExternalId_isNotBlank() {
            assertThat(isNotBlank("roleExternalId")).isTrue();
        }

        private boolean isNotBlank(String fieldName) {
            return PermCommonReqContractTest.this.isNotBlank(recordClass, fieldName);
        }

        private boolean isNotNull(String fieldName) {
            return PermCommonReqContractTest.this.isNotNull(recordClass, fieldName);
        }
    }

    // ========== UserRoleBatchRevokeReq.RevokeItem ==========

    @Nested
    @DisplayName("UserRoleBatchRevokeReq.RevokeItem")
    class RevokeItemContract {

        private final Class<?> recordClass =
            cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleBatchRevokeReq.RevokeItem.class;

        @Test
        @DisplayName("subjectTypeCode 必须 @NotBlank")
        void subjectTypeCode_isNotBlank() {
            assertThat(isNotBlank("subjectTypeCode")).isTrue();
        }

        @Test
        @DisplayName("subjectExternalId 必须 @NotBlank")
        void subjectExternalId_isNotBlank() {
            assertThat(isNotBlank("subjectExternalId")).isTrue();
        }

        @Test
        @DisplayName("domainCode 可为空（功能角色场景，M1 放宽）")
        void domainCode_isOptional() {
            assertThat(isNotBlank("domainCode")).isFalse();
            assertThat(isNotNull("domainCode")).isFalse();
        }

        @Test
        @DisplayName("roleTypeCode 必须 @NotBlank")
        void roleTypeCode_isNotBlank() {
            assertThat(isNotBlank("roleTypeCode")).isTrue();
        }

        @Test
        @DisplayName("roleExternalId 必须 @NotBlank")
        void roleExternalId_isNotBlank() {
            assertThat(isNotBlank("roleExternalId")).isTrue();
        }

        private boolean isNotBlank(String fieldName) {
            return PermCommonReqContractTest.this.isNotBlank(recordClass, fieldName);
        }

        private boolean isNotNull(String fieldName) {
            return PermCommonReqContractTest.this.isNotNull(recordClass, fieldName);
        }
    }

    // ========== helpers ==========

    private boolean isNotBlank(Class<?> recordClass, String fieldName) {
        try {
            // Record component annotations on compact constructor params
            // are accessible via getAnnotation if the annotation has @Target(FIELD)
            // For records, try both RecordComponent and constructor parameter
            RecordComponent comp = findComponent(recordClass, fieldName);
            if (comp.getAnnotation(NotBlank.class) != null) {
                return true;
            }
            // Fallback: check constructor parameter annotations
            return hasConstructorParamAnnotation(recordClass, fieldName, NotBlank.class);
        } catch (Exception e) {
            throw new AssertionError("Failed to check @NotBlank on " + recordClass.getSimpleName() + "." + fieldName, e);
        }
    }

    private boolean isNotNull(Class<?> recordClass, String fieldName) {
        try {
            RecordComponent comp = findComponent(recordClass, fieldName);
            if (comp.getAnnotation(NotNull.class) != null) {
                return true;
            }
            return hasConstructorParamAnnotation(recordClass, fieldName, NotNull.class);
        } catch (Exception e) {
            throw new AssertionError("Failed to check @NotNull on " + recordClass.getSimpleName() + "." + fieldName, e);
        }
    }

    /**
     * Check if the record's canonical constructor parameter has the given annotation.
     * Some record annotations are only present on the constructor parameter, not the component.
     */
    private boolean hasConstructorParamAnnotation(Class<?> recordClass, String fieldName,
                                                   Class<? extends java.lang.annotation.Annotation> annotationClass) {
        try {
            RecordComponent[] components = recordClass.getRecordComponents();
            int paramIndex = -1;
            for (int i = 0; i < components.length; i++) {
                if (components[i].getName().equals(fieldName)) {
                    paramIndex = i;
                    break;
                }
            }
            if (paramIndex < 0) return false;

            var ctor = recordClass.getDeclaredConstructors();
            for (var c : ctor) {
                var params = c.getParameters();
                if (params.length == components.length && paramIndex < params.length) {
                    return params[paramIndex].getAnnotation(annotationClass) != null;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private RecordComponent findComponent(Class<?> recordClass, String fieldName) {
        for (RecordComponent comp : recordClass.getRecordComponents()) {
            if (comp.getName().equals(fieldName)) {
                return comp;
            }
        }
        throw new AssertionError(
            "Field '" + fieldName + "' not found in " + recordClass.getSimpleName());
    }
}
