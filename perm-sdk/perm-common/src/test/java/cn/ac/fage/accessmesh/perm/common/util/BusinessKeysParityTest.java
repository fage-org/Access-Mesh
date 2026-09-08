package cn.ac.fage.accessmesh.perm.common.util;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys.RelationKeyRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务键格式 golden 锁（T-PERM-019 D2）。
 *
 * <p>每个键族断言精确输出字符串：任何格式变化（分隔符、顺序、null 语义、大写归一）都会在此红，
 * 而不是在运行时以缓存错配/映射失配的形式静默漏网。新增键族必须同步补 golden 用例。</p>
 */
class BusinessKeysParityTest {

    @Test
    void typeValueCacheKeyShouldLockTypeKeyTypeCodeFormat() {
        assertThat(BusinessKeys.typeValueCacheKey("resource_type", "USER")).isEqualTo("resource_type:USER");
    }

    @Test
    void typeCodeCacheKeyShouldLockTypeKeyTypeValueFormat() {
        assertThat(BusinessKeys.typeCodeCacheKey("resource_type", 3)).isEqualTo("resource_type:3");
    }

    @Test
    void generatedTypeCodeShouldLockUpperTypeKeyUnderscoreValueFormat() {
        assertThat(BusinessKeys.generatedTypeCode("resource_type", 12)).isEqualTo("RESOURCE_TYPE_12");
        assertThat(BusinessKeys.generatedTypeCode("user_type", 1)).isEqualTo("USER_TYPE_1");
    }

    @Test
    void typeInstanceBusinessKeyShouldLockCompositeKeyFormat() {
        assertThat(BusinessKeys.typeInstanceBusinessKey("user_type", "SERVICE")).isEqualTo("user_type:SERVICE");
    }

    @Test
    void operationCodeKeyShouldLockBothTracks() {
        assertThat(BusinessKeys.operationCodeKey("USER", "VIEW")).isEqualTo("USER:VIEW");
        assertThat(BusinessKeys.operationCodeKey(6, "VIEW")).isEqualTo("6:VIEW");
    }

    @Test
    void operationBitKeyShouldLockNullTypeSentinel() {
        assertThat(BusinessKeys.operationBitKey(6, 16L)).isEqualTo("6:16");
        assertThat(BusinessKeys.operationBitKey(null, 16L)).isEqualTo("NULL:16");
    }

    @Test
    void permissionCodeShouldLockExternalContractFormat() {
        assertThat(BusinessKeys.permissionCode("ROLE", "MANAGE")).isEqualTo("ROLE:MANAGE");
    }

    @Test
    void grantEntryKeyShouldLockTripleFormat() {
        assertThat(BusinessKeys.grantEntryKey(6, "VIEW", 1001L)).isEqualTo("6:VIEW:1001");
    }

    @Test
    void resourceCodeTypeKeyShouldLockPairFormat() {
        assertThat(BusinessKeys.resourceCodeTypeKey("res-1", "BIZ")).isEqualTo("res-1:BIZ");
    }

    @Test
    void resourceTripleValueKeyShouldLockValueTrackFormat() {
        assertThat(BusinessKeys.resourceTripleValueKey(7, "res-1", "BIZ")).isEqualTo("7:res-1:BIZ");
    }

    @Test
    void resourceTripleCodeKeyShouldLockUpperAndEmptyNullSemantics() {
        assertThat(BusinessKeys.resourceTripleCodeKey("user", "res-1", "BIZ")).isEqualTo("USER:res-1:BIZ");
        assertThat(BusinessKeys.resourceTripleCodeKey(null, null, null)).isEqualTo("::");
        assertThat(BusinessKeys.resourceTripleCodeKey("user", "res-1", null)).isEqualTo("USER:res-1:");
    }

    @Test
    void grantCheckKeyShouldLockFiveSegmentWildcardAndScopeSentinels() {
        assertThat(BusinessKeys.grantCheckKey("USER", null, null, "VIEW", true)).isEqualTo("USER:*:*:VIEW:ALL");
        assertThat(BusinessKeys.grantCheckKey("ROLE", "r-1", "BIZ", "MANAGE", false)).isEqualTo("ROLE:r-1:BIZ:MANAGE:SPECIFIC");
    }

    @Test
    void relationKeyRoundTripShouldLockContractFormat() {
        assertThat(BusinessKeys.relationKey("ORG", "2001")).isEqualTo("ORG:2001");
        assertThat(BusinessKeys.parseRelationKey("ORG:2001")).isEqualTo(new RelationKeyRef("ORG", "2001"));
        // relationKey 允许 externalId 内含冒号（首个冒号切分），与原三处私有解析一致
        assertThat(BusinessKeys.parseRelationKey("EXT:a:b")).isEqualTo(new RelationKeyRef("EXT", "a:b"));
    }

    @Test
    void parseRelationKeyShouldReturnNullForMalformedInput() {
        assertThat(BusinessKeys.parseRelationKey(null)).isNull();
        assertThat(BusinessKeys.parseRelationKey("  ")).isNull();
        assertThat(BusinessKeys.parseRelationKey("ORG")).isNull();
        assertThat(BusinessKeys.parseRelationKey(":2001")).isNull();
        assertThat(BusinessKeys.parseRelationKey("ORG:")).isNull();
    }

    @Test
    void subjectKeyShouldLockSubjectFormat() {
        assertThat(BusinessKeys.subjectKey("USER", "u-1")).isEqualTo("USER:u-1");
    }

    @Test
    void roleKeyShouldLockEmptyDomainSemantics() {
        assertThat(BusinessKeys.roleKey("BASIC_ROLE", "HR", "r-9")).isEqualTo("BASIC_ROLE:HR:r-9");
        assertThat(BusinessKeys.roleKey("BASIC_ROLE", null, "r-9")).isEqualTo("BASIC_ROLE::r-9");
    }

    @Test
    void userRoleRelationKeyShouldLockPairFormat() {
        assertThat(BusinessKeys.userRoleRelationKey(11L, 22L)).isEqualTo("11:22");
    }

    @Test
    void userRoleRelationIdKeyShouldLockLiteralNullSentinel() {
        assertThat(BusinessKeys.userRoleRelationIdKey(11L, 22L, 33L)).isEqualTo("11:22:33");
        assertThat(BusinessKeys.userRoleRelationIdKey(11L, 22L, null)).isEqualTo("11:22:null");
    }

    @Test
    void roleTypeDomainKeyShouldLockEmptyDomainSemantics() {
        assertThat(BusinessKeys.roleTypeDomainKey("BASIC_ROLE", "HR")).isEqualTo("BASIC_ROLE:HR");
        assertThat(BusinessKeys.roleTypeDomainKey("BASIC_ROLE", null)).isEqualTo("BASIC_ROLE:");
    }

    @Test
    void dependencyDiffKeyShouldLockZeroBitsSentinel() {
        assertThat(BusinessKeys.dependencyDiffKey(1L, 2L, 48L)).isEqualTo("1:2:48");
        assertThat(BusinessKeys.dependencyDiffKey(1L, 2L, null)).isEqualTo("1:2:0");
    }

    @Test
    void apiRouteKeyShouldLockMethodPathFormat() {
        assertThat(BusinessKeys.apiRouteKey("POST", "/api/perm/x/list")).isEqualTo("POST:/api/perm/x/list");
    }

    // -------------------- 竖线族（2026-09-08 codex 复评 P2-1 收编） --------------------

    @Test
    void permEntrySourceKeyShouldLockSixSegmentPipeFormat() {
        assertThat(BusinessKeys.permEntrySourceKey(11L, 22L, 1001L, 6, 48L, false))
            .isEqualTo("11|22|1001|6|48|false");
        // scopeAll 行 resourceEntityId 为 null（拼字面 "null"，与原始实现一致）
        assertThat(BusinessKeys.permEntrySourceKey(11L, 22L, null, 6, 255L, true))
            .isEqualTo("11|22|null|6|255|true");
    }

    @Test
    void inheritedEntryKeyShouldLockEntityPermissionPair() {
        assertThat(BusinessKeys.inheritedEntryKey(1001L, 11L)).isEqualTo("1001|11");
    }

    @Test
    void roleProjectionIndexKeyShouldLockTypeExternalPair() {
        assertThat(BusinessKeys.roleProjectionIndexKey("ORG", "2001")).isEqualTo("ORG|2001");
    }

    @Test
    void userRoleTripleKeyShouldLockTripleAndNullRelation() {
        assertThat(BusinessKeys.userRoleTripleKey(31L, 41L, 51L)).isEqualTo("31|41|51");
        assertThat(BusinessKeys.userRoleTripleKey(31L, 41L, null)).isEqualTo("31|41|null");
    }

    @Test
    void apiRouteResourceKeyShouldLockThreeSegmentPipeFormat() {
        assertThat(BusinessKeys.apiRouteResourceKey("POST", "/api/x", "res-1")).isEqualTo("POST|/api/x|res-1");
    }

    @Test
    void scopeItemKeyShouldLockCodeTypeCodeOrder() {
        assertThat(BusinessKeys.scopeItemKey("BIZ", "res-1")).isEqualTo("BIZ|res-1");
    }

    @Test
    void apiEntryDedupKeyShouldLockFourSegmentAndNullCondition() {
        assertThat(BusinessKeys.apiEntryDedupKey("demo-svc", "POST", "/api/x", 7L))
            .isEqualTo("demo-svc|POST|/api/x|7");
        // 无条件分支 conditionId=null 拼字面 "null"，与任何条件分支独立保留（T-PERM-017 C4）
        assertThat(BusinessKeys.apiEntryDedupKey("demo-svc", "POST", "/api/x", null))
            .isEqualTo("demo-svc|POST|/api/x|null");
    }

    @Test
    void apiMappingPresenceKeyShouldLockIndexIsomorphicFormat() {
        assertThat(BusinessKeys.apiMappingPresenceKey("demo-svc", 1001L, "get", "/api/x"))
            .isEqualTo("demo-svc|1001|GET|/api/x");
    }

    /**
     * 纯拼接族的 null 语义锁：null 引用按字符串拼接规则产出字面 "null"（与被收敛的原始实现一致），
     * 防未来有人给这些方法加 null 拒绝分支而不自知——部分调用点的 null 入参是可达分支
     * （如 grantEntryKey 的 resourceEntityId 为 null 时产出 "…:null" 是授权域查找的依赖行为）。
     */
    @Test
    void concatFamiliesShouldLockLiteralNullSemantics() {
        assertThat(BusinessKeys.typeValueCacheKey(null, "USER")).isEqualTo("null:USER");
        assertThat(BusinessKeys.typeCodeCacheKey("resource_type", null)).isEqualTo("resource_type:null");
        assertThat(BusinessKeys.operationCodeKey((String) null, "VIEW")).isEqualTo("null:VIEW");
        assertThat(BusinessKeys.operationCodeKey(6, null)).isEqualTo("6:null");
        assertThat(BusinessKeys.operationBitKey(6, null)).isEqualTo("6:null");
        assertThat(BusinessKeys.permissionCode(null, null)).isEqualTo("null:null");
        assertThat(BusinessKeys.grantEntryKey(6, "VIEW", null)).isEqualTo("6:VIEW:null");
        assertThat(BusinessKeys.resourceCodeTypeKey(null, "BIZ")).isEqualTo("null:BIZ");
        assertThat(BusinessKeys.resourceTripleValueKey(7, null, null)).isEqualTo("7:null:null");
        assertThat(BusinessKeys.subjectKey(null, "u-1")).isEqualTo("null:u-1");
        assertThat(BusinessKeys.userRoleRelationKey(null, 22L)).isEqualTo("null:22");
        assertThat(BusinessKeys.apiRouteKey(null, "/x")).isEqualTo("null:/x");
        assertThat(BusinessKeys.relationKey(null, "2001")).isEqualTo("null:2001");
        assertThat(BusinessKeys.dependencyDiffKey(null, 2L, null)).isEqualTo("null:2:0");
    }
}
