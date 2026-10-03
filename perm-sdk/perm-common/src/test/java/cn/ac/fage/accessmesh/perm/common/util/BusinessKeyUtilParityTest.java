package cn.ac.fage.accessmesh.perm.common.util;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil.RelationKeyRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务键格式 golden 锁（T-PERM-019 D2）。
 *
 * <p>每个键族断言精确输出字符串：任何格式变化（分隔符、顺序、null 语义、大写归一）都会在此红，
 * 而不是在运行时以缓存错配/映射失配的形式静默漏网。新增键族必须同步补 golden 用例。</p>
 */
class BusinessKeyUtilParityTest {

    @Test
    void typeValueCacheKeyShouldLockTypeKeyTypeCodeFormat() {
        assertThat(BusinessKeyUtil.typeValueCacheKey("resource_type", "USER")).isEqualTo("resource_type:USER");
    }

    @Test
    void typeCodeCacheKeyShouldLockTypeKeyTypeValueFormat() {
        assertThat(BusinessKeyUtil.typeCodeCacheKey("resource_type", 3)).isEqualTo("resource_type:3");
    }

    @Test
    void generatedTypeCodeShouldLockUpperTypeKeyUnderscoreValueFormat() {
        assertThat(BusinessKeyUtil.generatedTypeCode("resource_type", 12)).isEqualTo("RESOURCE_TYPE_12");
        assertThat(BusinessKeyUtil.generatedTypeCode("user_type", 1)).isEqualTo("USER_TYPE_1");
    }

    @Test
    void typeInstanceBusinessKeyShouldLockCompositeKeyFormat() {
        assertThat(BusinessKeyUtil.typeInstanceBusinessKey("user_type", "SERVICE")).isEqualTo("user_type:SERVICE");
    }

    @Test
    void operationCodeKeyShouldLockBothTracks() {
        assertThat(BusinessKeyUtil.operationCodeKey("USER", "VIEW")).isEqualTo("USER:VIEW");
        assertThat(BusinessKeyUtil.operationCodeKey(6, "VIEW")).isEqualTo("6:VIEW");
    }

    @Test
    void operationBitKeyShouldLockNullTypeSentinel() {
        assertThat(BusinessKeyUtil.operationBitKey(6, 16L)).isEqualTo("6:16");
        assertThat(BusinessKeyUtil.operationBitKey(null, 16L)).isEqualTo("NULL:16");
    }

    @Test
    void permissionCodeShouldLockExternalContractFormat() {
        assertThat(BusinessKeyUtil.permissionCode("ROLE", "MANAGE")).isEqualTo("ROLE:MANAGE");
    }

    @Test
    void grantEntryKeyShouldLockTripleFormat() {
        assertThat(BusinessKeyUtil.grantEntryKey(6, "VIEW", 1001L)).isEqualTo("6:VIEW:1001");
    }

    // 注：resourceCodeTypeKey / resourceTripleCodeKey / grantCheckKey / resourceTripleValueKey
    // 的 golden 已随 T-PERM-096 元组化退役删除——四个内存索引键构造器从 BusinessKeyUtil 移除
    // （前三个改结构化 record 键、resourceTripleValueKey 消费方先期已改 TripleKey record），
    // 碰撞对回归锁在各消费方测试。
    // 注：subjectKey / roleKey / roleTypeDomainKey 的 golden 已随 T-PERM-104 元组化退役删除——
    // 三个用户角色定位键构造器从 BusinessKeyUtil 移除（唯一消费方 UserManageAppServiceImpl 改
    // 类内 record 元组键，Q-057 滑移碰撞收口），碰撞对回归锁在 UserManageAppServiceImplTest。

    @Test
    void relationKeyRoundTripShouldLockContractFormat() {
        assertThat(BusinessKeyUtil.relationKey("ORG", "2001")).isEqualTo("ORG:2001");
        assertThat(BusinessKeyUtil.parseRelationKey("ORG:2001")).isEqualTo(new RelationKeyRef("ORG", "2001"));
        // relationKey 允许 externalId 内含冒号（首个冒号切分），与原三处私有解析一致
        assertThat(BusinessKeyUtil.parseRelationKey("EXT:a:b")).isEqualTo(new RelationKeyRef("EXT", "a:b"));
    }

    @Test
    void parseRelationKeyShouldReturnNullForMalformedInput() {
        assertThat(BusinessKeyUtil.parseRelationKey(null)).isNull();
        assertThat(BusinessKeyUtil.parseRelationKey("  ")).isNull();
        assertThat(BusinessKeyUtil.parseRelationKey("ORG")).isNull();
        assertThat(BusinessKeyUtil.parseRelationKey(":2001")).isNull();
        assertThat(BusinessKeyUtil.parseRelationKey("ORG:")).isNull();
    }

    @Test
    void userRoleRelationIdKeyShouldLockLiteralNullSentinel() {
        assertThat(BusinessKeyUtil.userRoleRelationIdKey(11L, 22L, 33L)).isEqualTo("11:22:33");
        assertThat(BusinessKeyUtil.userRoleRelationIdKey(11L, 22L, null)).isEqualTo("11:22:null");
    }

    @Test
    void dependencyDiffKeyShouldLockZeroBitsSentinel() {
        assertThat(BusinessKeyUtil.dependencyDiffKey(1L, 2L, 48L)).isEqualTo("1:2:48");
        assertThat(BusinessKeyUtil.dependencyDiffKey(1L, 2L, null)).isEqualTo("1:2:0");
    }

    @Test
    void apiRouteKeyShouldLockMethodPathFormat() {
        assertThat(BusinessKeyUtil.apiRouteKey("POST", "/api/access/x/list")).isEqualTo("POST:/api/access/x/list");
    }

    // -------------------- 竖线族（2026-09-08 codex 复评 P2-1 收编） --------------------

    @Test
    void roleProjectionIndexKeyShouldLockTypeExternalPair() {
        assertThat(BusinessKeyUtil.roleProjectionIndexKey("ORG", "2001")).isEqualTo("ORG|2001");
    }

    @Test
    void userRoleTripleKeyShouldLockTripleAndNullRelation() {
        assertThat(BusinessKeyUtil.userRoleTripleKey(31L, 41L, 51L)).isEqualTo("31|41|51");
        assertThat(BusinessKeyUtil.userRoleTripleKey(31L, 41L, null)).isEqualTo("31|41|null");
    }

    @Test
    void apiRouteResourceKeyShouldLockThreeSegmentPipeFormat() {
        assertThat(BusinessKeyUtil.apiRouteResourceKey("POST", "/api/x", "res-1")).isEqualTo("POST|/api/x|res-1");
    }

    @Test
    void apiMappingPresenceKeyShouldLockIndexIsomorphicFormat() {
        assertThat(BusinessKeyUtil.apiMappingPresenceKey("demo-svc", 1001L, "get", "/api/x"))
            .isEqualTo("demo-svc|1001|GET|/api/x");
    }

    /**
     * 纯拼接族的 null 语义锁：null 引用按字符串拼接规则产出字面 "null"（与被收敛的原始实现一致），
     * 防未来有人给这些方法加 null 拒绝分支而不自知——部分调用点的 null 入参是可达分支
     * （如 grantEntryKey 的 resourceEntityId 为 null 时产出 "…:null" 是授权域查找的依赖行为）。
     */
    @Test
    void concatFamiliesShouldLockLiteralNullSemantics() {
        assertThat(BusinessKeyUtil.typeValueCacheKey(null, "USER")).isEqualTo("null:USER");
        assertThat(BusinessKeyUtil.typeCodeCacheKey("resource_type", null)).isEqualTo("resource_type:null");
        assertThat(BusinessKeyUtil.operationCodeKey((String) null, "VIEW")).isEqualTo("null:VIEW");
        assertThat(BusinessKeyUtil.operationCodeKey(6, null)).isEqualTo("6:null");
        assertThat(BusinessKeyUtil.operationBitKey(6, null)).isEqualTo("6:null");
        assertThat(BusinessKeyUtil.permissionCode(null, null)).isEqualTo("null:null");
        assertThat(BusinessKeyUtil.grantEntryKey(6, "VIEW", null)).isEqualTo("6:VIEW:null");
        assertThat(BusinessKeyUtil.userRoleRelationIdKey(null, 22L, 33L)).isEqualTo("null:22:33");
        assertThat(BusinessKeyUtil.apiRouteKey(null, "/x")).isEqualTo("null:/x");
        assertThat(BusinessKeyUtil.relationKey(null, "2001")).isEqualTo("null:2001");
        assertThat(BusinessKeyUtil.dependencyDiffKey(null, 2L, null)).isEqualTo("null:2:0");
    }
}
