package cn.ac.fage.accessmesh.access.admin.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 验证 {@link OrgOperationCodeMapper} 的 null 安全性和 orgType 分发逻辑。
 * <p>
 * 重点覆盖 {@code Map.of().get(null)} NPE 回归（normalize 必须将 null 映射到 "1"）。
 */
class OrgOperationCodeMapperTest {

    // ===== resolve: CRUD 操作码映射 =====

    @Nested
    @DisplayName("resolve — CRUD/VIEW 操作码")
    class Resolve {

        @Test
        @DisplayName("null orgType + CREATE → 回退到基础操作码 CREATE（不 NPE）")
        void resolve_nullOrgType_CREATE_noNPE() {
            assertThatNoException().isThrownBy(() ->
                OrgOperationCodeMapper.resolve(null, AdminOperationCode.CREATE));
            assertThat(OrgOperationCodeMapper.resolve(null, AdminOperationCode.CREATE))
                .isEqualTo(AdminOperationCode.CREATE);
        }

        @Test
        @DisplayName("null orgType + UPDATE → 回退到 UPDATE（不 NPE）")
        void resolve_nullOrgType_UPDATE_noNPE() {
            assertThat(OrgOperationCodeMapper.resolve(null, AdminOperationCode.UPDATE))
                .isEqualTo(AdminOperationCode.UPDATE);
        }

        @Test
        @DisplayName("null orgType + DELETE → 回退到 DELETE")
        void resolve_nullOrgType_DELETE_noNPE() {
            assertThat(OrgOperationCodeMapper.resolve(null, AdminOperationCode.DELETE))
                .isEqualTo(AdminOperationCode.DELETE);
        }

        @Test
        @DisplayName("null orgType + VIEW → 回退到 VIEW")
        void resolve_nullOrgType_VIEW_noNPE() {
            assertThat(OrgOperationCodeMapper.resolve(null, AdminOperationCode.VIEW))
                .isEqualTo(AdminOperationCode.VIEW);
        }

        @Test
        @DisplayName("\"1\" orgType + CREATE → 回退到 CREATE（普通组织）")
        void resolve_regularNum_CREATE() {
            assertThat(OrgOperationCodeMapper.resolve("1", AdminOperationCode.CREATE))
                .isEqualTo(AdminOperationCode.CREATE);
        }

        @Test
        @DisplayName("\"ORG\" orgType + UPDATE → UPDATE")
        void resolve_regularLabel_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolve("ORG", AdminOperationCode.UPDATE))
                .isEqualTo(AdminOperationCode.UPDATE);
        }

        @Test
        @DisplayName("\"2\" orgType + CREATE → CREATE_POSITION")
        void resolve_positionNum_CREATE() {
            assertThat(OrgOperationCodeMapper.resolve("2", AdminOperationCode.CREATE))
                .isEqualTo(AdminOperationCode.CREATE_POSITION);
        }

        @Test
        @DisplayName("\"POSITION\" orgType + VIEW → VIEW_POSITION")
        void resolve_positionLabel_VIEW() {
            assertThat(OrgOperationCodeMapper.resolve("POSITION", AdminOperationCode.VIEW))
                .isEqualTo(AdminOperationCode.VIEW_POSITION);
        }

        @Test
        @DisplayName("\"2\" orgType + UPDATE → UPDATE_POSITION")
        void resolve_positionNum_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolve("2", AdminOperationCode.UPDATE))
                .isEqualTo(AdminOperationCode.UPDATE_POSITION);
        }

        @Test
        @DisplayName("\"2\" orgType + DELETE → DELETE_POSITION")
        void resolve_positionNum_DELETE() {
            assertThat(OrgOperationCodeMapper.resolve("2", AdminOperationCode.DELETE))
                .isEqualTo(AdminOperationCode.DELETE_POSITION);
        }
    }

    // ===== resolveForUserOrg: 成员关系操作码映射 =====

    @Nested
    @DisplayName("resolveForUserOrg — 成员关系操作码")
    class ResolveForUserOrg {

        @Test
        @DisplayName("null orgType + UPDATE → MANAGE_MEMBER（不 NPE，核心回归守卫）")
        void resolveForUserOrg_nullOrgType_UPDATE_noNPE() {
            assertThatNoException().isThrownBy(() ->
                OrgOperationCodeMapper.resolveForUserOrg(null, AdminOperationCode.UPDATE));
            assertThat(OrgOperationCodeMapper.resolveForUserOrg(null, AdminOperationCode.UPDATE))
                .isEqualTo(AdminOperationCode.MANAGE_MEMBER);
        }

        @Test
        @DisplayName("\"1\" orgType + UPDATE → MANAGE_MEMBER")
        void resolveForUserOrg_regularNum_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("1", AdminOperationCode.UPDATE))
                .isEqualTo(AdminOperationCode.MANAGE_MEMBER);
        }

        @Test
        @DisplayName("\"ORG\" orgType + UPDATE → MANAGE_MEMBER")
        void resolveForUserOrg_regularLabel_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("ORG", AdminOperationCode.UPDATE))
                .isEqualTo(AdminOperationCode.MANAGE_MEMBER);
        }

        @Test
        @DisplayName("\"2\" orgType + UPDATE → ASSIGN_POSITION_USER")
        void resolveForUserOrg_positionNum_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("2", AdminOperationCode.UPDATE))
                .isEqualTo(AdminOperationCode.ASSIGN_POSITION_USER);
        }

        @Test
        @DisplayName("\"POSITION\" orgType + UPDATE → ASSIGN_POSITION_USER")
        void resolveForUserOrg_positionLabel_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("POSITION", AdminOperationCode.UPDATE))
                .isEqualTo(AdminOperationCode.ASSIGN_POSITION_USER);
        }

        @Test
        @DisplayName("null orgType + CREATE → 回退到 resolve（CREATE）")
        void resolveForUserOrg_nullOrgType_CREATE_fallback() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg(null, AdminOperationCode.CREATE))
                .isEqualTo(AdminOperationCode.CREATE);
        }

        @Test
        @DisplayName("\"2\" orgType + DELETE → 回退到 resolve（DELETE_POSITION）")
        void resolveForUserOrg_positionNum_DELETE_fallback() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("2", AdminOperationCode.DELETE))
                .isEqualTo(AdminOperationCode.DELETE_POSITION);
        }
    }

    // ===== isPositionOrg =====

    @Nested
    @DisplayName("isPositionOrg")
    class IsPositionOrg {

        @Test
        @DisplayName("null → false")
        void isPositionOrg_null() {
            assertThat(OrgOperationCodeMapper.isPositionOrg(null)).isFalse();
        }

        @Test
        @DisplayName("\"1\" → false")
        void isPositionOrg_regularNum() {
            assertThat(OrgOperationCodeMapper.isPositionOrg("1")).isFalse();
        }

        @Test
        @DisplayName("\"ORG\" → false")
        void isPositionOrg_regularLabel() {
            assertThat(OrgOperationCodeMapper.isPositionOrg("ORG")).isFalse();
        }

        @Test
        @DisplayName("\"2\" → true")
        void isPositionOrg_positionNum() {
            assertThat(OrgOperationCodeMapper.isPositionOrg("2")).isTrue();
        }

        @Test
        @DisplayName("\"POSITION\" → true")
        void isPositionOrg_positionLabel() {
            assertThat(OrgOperationCodeMapper.isPositionOrg("POSITION")).isTrue();
        }
    }
}
