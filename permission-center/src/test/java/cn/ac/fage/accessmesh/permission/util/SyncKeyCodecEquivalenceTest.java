package cn.ac.fage.accessmesh.permission.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * USER_ROLE 增量 sync 与 full-sync scopeKey 等价性回归测试。
 * <p>
 * 背景：增量 sync 与 full-sync 在写 sync_metadata 时必须使用相同的 scopeKey，否则全量校准
 * 阶段无法定位增量阶段写入的元数据，导致 deactivate 误清理或孤儿存活。
 * </p>
 * <p>
 * 等价性条件：给定相同的 (roleTypeCode, treeRootExternalId)，
 * 调用 {@link SyncKeyCodec#userRoleScopeKey(String, String)} 必须输出字面相同的字符串。
 * </p>
 */
class SyncKeyCodecEquivalenceTest {

    @Test
    void shouldProduceIdenticalScopeKey_whenRoleTypeIsOrg() {
        String incremental = SyncKeyCodec.userRoleScopeKey("ORG", "1");
        String fullSync = SyncKeyCodec.userRoleScopeKey("ORG", "1");

        assertThat(incremental).isEqualTo(fullSync);
        assertThat(incremental)
                .isEqualTo("sourceType=SYS_USER_ORG&roleTypeCode=ORG&treeRootExternalId=1");
    }

    @Test
    void shouldProduceIdenticalScopeKey_whenRoleTypeIsPosition() {
        String incremental = SyncKeyCodec.userRoleScopeKey("POSITION", "tree-root-42");
        String fullSync = SyncKeyCodec.userRoleScopeKey("POSITION", "tree-root-42");

        assertThat(incremental).isEqualTo(fullSync);
        assertThat(incremental)
                .isEqualTo("sourceType=SYS_USER_ORG&roleTypeCode=POSITION&treeRootExternalId=tree-root-42");
    }

    @Test
    void shouldProduceDifferentScopeKey_whenTreeRootDiffers() {
        // 防回归：保证 treeRootExternalId 真正参与了 scopeKey 编码
        String tree1 = SyncKeyCodec.userRoleScopeKey("ORG", "1");
        String tree2 = SyncKeyCodec.userRoleScopeKey("ORG", "2");

        assertThat(tree1).isNotEqualTo(tree2);
    }
}
