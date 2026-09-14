package cn.ac.fage.accessmesh.access.sync;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SyncKeyCodecUtil} 单元测试。
 * <p>
 * 严格按 docs/design/access-service-api-contract.md §19.7 表格断言：
 * <ul>
 *   <li>4 类 businessKey 与 4 类 scopeKey 字段顺序与样例一致；</li>
 *   <li>relationKey {@code ORG:2001} percent-encode 为 {@code ORG%3A2001}；</li>
 *   <li>SHA-256 hex 长度 64 且全小写。</li>
 * </ul>
 */
class SyncKeyCodecUtilTest {

    @Test
    void abstractUserBusinessKey_shouldUseFixedFieldOrder() {
        String key = SyncKeyCodecUtil.abstractUserBusinessKey("USER", "u-001");
        assertEquals("subjectTypeCode=USER&subjectExternalId=u-001", key);
    }

    @Test
    void abstractRoleBusinessKey_shouldUseFixedFieldOrder() {
        String key = SyncKeyCodecUtil.abstractRoleBusinessKey("ORG", "org-100");
        assertEquals("roleTypeCode=ORG&roleExternalId=org-100", key);
    }

    @Test
    void resourceEntityBusinessKey_shouldIncludeCodeType() {
        String key = SyncKeyCodecUtil.resourceEntityBusinessKey("ORG", "1001", "default");
        assertEquals("resourceTypeCode=ORG&resourceCode=1001&codeType=default", key);
    }

    @Test
    void userRoleBusinessKey_shouldPercentEncodeRelationKey() {
        String key = SyncKeyCodecUtil.userRoleBusinessKey(
                "USER", "u-001", "ORG", "org-100", "ORG:2001");
        // relationKey=ORG:2001 必须写为 relationKey=ORG%3A2001
        assertEquals(
                "subjectTypeCode=USER&subjectExternalId=u-001"
                        + "&roleTypeCode=ORG&roleExternalId=org-100"
                        + "&relationKey=ORG%3A2001",
                key);
        assertTrue(key.contains("ORG%3A2001"));
    }

    @Test
    void abstractUserScopeKey_shouldUseSubjectTypeCodeOnly() {
        assertEquals("subjectTypeCode=USER", SyncKeyCodecUtil.abstractUserScopeKey("USER"));
    }

    @Test
    void abstractRoleScopeKey_shouldIncludeTreeRoot() {
        assertEquals(
                "roleTypeCode=ORG&treeRootExternalId=root",
                SyncKeyCodecUtil.abstractRoleScopeKey("ORG", "root"));
    }

    @Test
    void resourceEntityScopeKey_shouldUseResourceTypeCodeOnly() {
        assertEquals(
                "resourceTypeCode=ORG",
                SyncKeyCodecUtil.resourceEntityScopeKey("ORG"));
    }

    @Test
    void userRoleScopeKey_shouldUseCallerSourceType() {
        String key = SyncKeyCodecUtil.userRoleScopeKey("HR_MEMBER", "ORG", "root-1");
        assertEquals(
                "sourceType=HR_MEMBER&roleTypeCode=ORG&treeRootExternalId=root-1",
                key);
    }

    @Test
    void encodeBusinessKey_shouldKeepInsertionOrder() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("b", "2");
        map.put("a", "1");
        assertEquals("b=2&a=1", SyncKeyCodecUtil.encodeBusinessKey(map));
    }

    @Test
    void sha256Hex_shouldBeLowerCaseAndLength64() {
        String hex = SyncKeyCodecUtil.sha256Hex("subjectTypeCode=USER&subjectExternalId=u-001");
        assertEquals(64, hex.length());
        assertEquals(hex.toLowerCase(), hex);
        // 正确性检查：不同输入产生不同 hash
        assertNotEquals(hex, SyncKeyCodecUtil.sha256Hex("different"));
    }
}
