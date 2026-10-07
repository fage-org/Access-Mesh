package cn.ac.fage.accessmesh.access.engine.constant;

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
                OrgOperationCodeMapper.resolve(null, OperationCode.CREATE));
            assertThat(OrgOperationCodeMapper.resolve(null, OperationCode.CREATE))
                .isEqualTo(OperationCode.CREATE);
        }

        @Test
        @DisplayName("null orgType + UPDATE → 回退到 UPDATE（不 NPE）")
        void resolve_nullOrgType_UPDATE_noNPE() {
            assertThat(OrgOperationCodeMapper.resolve(null, OperationCode.UPDATE))
                .isEqualTo(OperationCode.UPDATE);
        }

        @Test
        @DisplayName("null orgType + DELETE → 回退到 DELETE")
        void resolve_nullOrgType_DELETE_noNPE() {
            assertThat(OrgOperationCodeMapper.resolve(null, OperationCode.DELETE))
                .isEqualTo(OperationCode.DELETE);
        }

        @Test
        @DisplayName("null orgType + VIEW → 回退到 VIEW")
        void resolve_nullOrgType_VIEW_noNPE() {
            assertThat(OrgOperationCodeMapper.resolve(null, OperationCode.VIEW))
                .isEqualTo(OperationCode.VIEW);
        }

        @Test
        @DisplayName("\"1\" orgType + CREATE → 回退到 CREATE（普通组织）")
        void resolve_regularNum_CREATE() {
            assertThat(OrgOperationCodeMapper.resolve("1", OperationCode.CREATE))
                .isEqualTo(OperationCode.CREATE);
        }

        @Test
        @DisplayName("\"ORG\" orgType + UPDATE → UPDATE")
        void resolve_regularLabel_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolve("ORG", OperationCode.UPDATE))
                .isEqualTo(OperationCode.UPDATE);
        }

        @Test
        @DisplayName("\"2\" orgType + CREATE → CREATE_POSITION")
        void resolve_positionNum_CREATE() {
            assertThat(OrgOperationCodeMapper.resolve("2", OperationCode.CREATE))
                .isEqualTo(OperationCode.CREATE_POSITION);
        }

        @Test
        @DisplayName("\"POSITION\" orgType + VIEW → VIEW_POSITION")
        void resolve_positionLabel_VIEW() {
            assertThat(OrgOperationCodeMapper.resolve("POSITION", OperationCode.VIEW))
                .isEqualTo(OperationCode.VIEW_POSITION);
        }

        @Test
        @DisplayName("\"2\" orgType + UPDATE → UPDATE_POSITION")
        void resolve_positionNum_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolve("2", OperationCode.UPDATE))
                .isEqualTo(OperationCode.UPDATE_POSITION);
        }

        @Test
        @DisplayName("\"2\" orgType + DELETE → DELETE_POSITION")
        void resolve_positionNum_DELETE() {
            assertThat(OrgOperationCodeMapper.resolve("2", OperationCode.DELETE))
                .isEqualTo(OperationCode.DELETE_POSITION);
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
                OrgOperationCodeMapper.resolveForUserOrg(null, OperationCode.UPDATE));
            assertThat(OrgOperationCodeMapper.resolveForUserOrg(null, OperationCode.UPDATE))
                .isEqualTo(OperationCode.MANAGE_MEMBER);
        }

        @Test
        @DisplayName("\"1\" orgType + UPDATE → MANAGE_MEMBER")
        void resolveForUserOrg_regularNum_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("1", OperationCode.UPDATE))
                .isEqualTo(OperationCode.MANAGE_MEMBER);
        }

        @Test
        @DisplayName("\"ORG\" orgType + UPDATE → MANAGE_MEMBER")
        void resolveForUserOrg_regularLabel_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("ORG", OperationCode.UPDATE))
                .isEqualTo(OperationCode.MANAGE_MEMBER);
        }

        @Test
        @DisplayName("\"2\" orgType + UPDATE → ASSIGN_POSITION_USER")
        void resolveForUserOrg_positionNum_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("2", OperationCode.UPDATE))
                .isEqualTo(OperationCode.ASSIGN_POSITION_USER);
        }

        @Test
        @DisplayName("\"POSITION\" orgType + UPDATE → ASSIGN_POSITION_USER")
        void resolveForUserOrg_positionLabel_UPDATE() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("POSITION", OperationCode.UPDATE))
                .isEqualTo(OperationCode.ASSIGN_POSITION_USER);
        }

        @Test
        @DisplayName("null orgType + CREATE → 回退到 resolve（CREATE）")
        void resolveForUserOrg_nullOrgType_CREATE_fallback() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg(null, OperationCode.CREATE))
                .isEqualTo(OperationCode.CREATE);
        }

        @Test
        @DisplayName("\"2\" orgType + DELETE → 回退到 resolve（DELETE_POSITION）")
        void resolveForUserOrg_positionNum_DELETE_fallback() {
            assertThat(OrgOperationCodeMapper.resolveForUserOrg("2", OperationCode.DELETE))
                .isEqualTo(OperationCode.DELETE_POSITION);
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

    // ===== parseWireOrgType: 响应组装读面线格式解析（Q-066 短期防御） =====

    @Nested
    @DisplayName("parseWireOrgType — orgType VARCHAR → Integer 线格式")
    class ParseWireOrgType {

        @Test
        @DisplayName("null → null（读面降级，不抛异常——旧读面 Integer.parseInt(null) 直接 NumberFormatException）")
        void parseWireOrgType_null() {
            assertThat(OrgOperationCodeMapper.parseWireOrgType(null)).isNull();
        }

        @Test
        @DisplayName("空白串 → null")
        void parseWireOrgType_blank() {
            assertThat(OrgOperationCodeMapper.parseWireOrgType(" ")).isNull();
        }

        @Test
        @DisplayName("\"1\" → 1、\"2\" → 2（现行写入形态原样）")
        void parseWireOrgType_numericForms() {
            assertThat(OrgOperationCodeMapper.parseWireOrgType("1")).isEqualTo(1);
            assertThat(OrgOperationCodeMapper.parseWireOrgType("2")).isEqualTo(2);
        }

        @Test
        @DisplayName("历史标签 \"ORG\"→1、\"POSITION\"→2（与 normalize 同源归一，大小写不敏感）")
        void parseWireOrgType_labelForms() {
            assertThat(OrgOperationCodeMapper.parseWireOrgType("ORG")).isEqualTo(1);
            assertThat(OrgOperationCodeMapper.parseWireOrgType("POSITION")).isEqualTo(2);
            assertThat(OrgOperationCodeMapper.parseWireOrgType("position")).isEqualTo(2);
        }

        @Test
        @DisplayName("字典扩展数值 \"3\" → 3（不丢信息）")
        void parseWireOrgType_extendedNumeric() {
            assertThat(OrgOperationCodeMapper.parseWireOrgType("3")).isEqualTo(3);
        }

        @Test
        @DisplayName("无法识别的非数值标签 → null（脏数据降级，不中断读取）")
        void parseWireOrgType_garbageLabel() {
            assertThat(OrgOperationCodeMapper.parseWireOrgType("dept-01")).isNull();
        }
    }
}
