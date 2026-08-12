package cn.ac.fage.accessmesh.access.admin.sync;

import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link SyncTaskBuilder} 的全量校准 envelope 工厂（S6）：
 * <ul>
 *   <li>payload JSON 顶层含 {@code scope + items}，scope 字段对齐 §6.2.2.4 scopeKey 表</li>
 *   <li>每个 item 含 {@code syncVersion}，sequenceNo 严格递增</li>
 *   <li>businessKey 含 phase 和对应的 scope 字段</li>
 *   <li>batchKey 透传，handler 据此走 full-sync 分支</li>
 *   <li>displayAttrs 含 entityType/operationType=FULL_SYNC/itemCount</li>
 * </ul>
 */
class SyncTaskBuilderFullSyncTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SyncSequenceProvider sequenceProvider;
    private SyncTaskBuilder builder;

    private static final String BATCH_KEY = "sourceService=admin-service&runId=test-run-id";

    @BeforeEach
    void setUp() {
        sequenceProvider = new SyncSequenceProvider();
        builder = new SyncTaskBuilder(objectMapper, sequenceProvider);
    }

    @Test
    @DisplayName("userFullSync: payload 顶层含 scope + items，items 每条含 subjectExternalId 和 syncVersion")
    void userFullSync_shapeIsCorrect() throws Exception {
        SysUser u1 = new SysUser();
        u1.setId(10001L);
        u1.setUsername("zhangsan");
        u1.setName("张三");
        u1.setStatus(1);

        SysUser u2 = new SysUser();
        u2.setId(10002L);
        u2.setUsername("lisi");
        u2.setName("李四");
        u2.setStatus(1);

        SyncTaskEnvelope env = builder.userFullSync(1L, List.of(u1, u2), BATCH_KEY, "USER_SUBJECT");

        // batchKey 透传
        assertThat(env.batchKey()).isEqualTo(BATCH_KEY);
        assertThat(env.phase()).isEqualTo("USER_SUBJECT");

        JsonNode root = objectMapper.readTree(env.payload());
        assertThat(root.has("scope")).isTrue();
        assertThat(root.get("scope").get("sourceService").asText()).isEqualTo("admin-service");
        assertThat(root.get("scope").get("subjectTypeCode").asText()).isEqualTo("ADMIN_USER");

        JsonNode items = root.get("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(2);

        JsonNode item0 = items.get(0);
        assertThat(item0.get("subjectExternalId").asText()).isEqualTo("10001");
        assertThat(item0.has("syncVersion")).isTrue();
        assertThat(item0.get("syncVersion").has("sequenceNo")).isTrue();
        assertThat(item0.get("syncVersion").has("occurredAt")).isTrue();

        // displayAttrs
        assertThat(env.displayAttrs()).containsEntry("entityType", "sys_user_full_sync");
        assertThat(env.displayAttrs()).containsEntry("operationType", "FULL_SYNC");
        assertThat(env.displayAttrs()).containsEntry("itemCount", 2);

        // businessKey 含 phase 和 scope 字段
        assertThat(env.businessKey()).contains("phase=USER_SUBJECT");
        assertThat(env.businessKey()).contains("subjectTypeCode=ADMIN_USER");
    }

    @Test
    @DisplayName("orgRoleFullSync: scope 含 roleTypeCode + treeRootExternalId；items 含 roleExternalId + parentRoleExternalId")
    void orgRoleFullSync_shapeIsCorrect() throws Exception {
        SysOrg org = new SysOrg();
        org.setId(2001L);
        org.setName("研发部");
        org.setParentId(1000L);
        org.setStatus(1);
        org.setSortOrder(10);
        org.setOrgType("ORG");

        SyncTaskEnvelope env = builder.orgRoleFullSync(1L, List.of(org), BATCH_KEY, "ORG_ROLE",
            "ORG", "1");

        assertThat(env.batchKey()).isEqualTo(BATCH_KEY);

        JsonNode root = objectMapper.readTree(env.payload());
        JsonNode scope = root.get("scope");
        assertThat(scope.get("sourceService").asText()).isEqualTo("admin-service");
        assertThat(scope.get("roleTypeCode").asText()).isEqualTo("ORG");
        assertThat(scope.get("treeRootExternalId").asText()).isEqualTo("1");

        JsonNode item = root.get("items").get(0);
        assertThat(item.get("roleExternalId").asText()).isEqualTo("2001");
        assertThat(item.get("parentRoleExternalId").asText()).isEqualTo("1000");
        assertThat(item.has("syncVersion")).isTrue();

        // businessKey
        assertThat(env.businessKey()).contains("phase=ORG_ROLE");
        assertThat(env.businessKey()).contains("roleTypeCode=ORG");
        assertThat(env.businessKey()).contains("treeRootExternalId=1");
    }

    @Test
    @DisplayName("userOrgFullSync: scope 含 sourceType=SYS_USER_ORG + roleTypeCode + treeRootExternalId；items 含 percent-encoded relationKey")
    void userOrgFullSync_shapeIsCorrect() throws Exception {
        SysUserOrg b = new SysUserOrg();
        b.setUserId(10001L);
        b.setOrgId(2001L);

        SyncTaskEnvelope env = builder.userOrgFullSync(1L, List.of(b),
            BATCH_KEY, "USER_ROLE",
            "ORG", "1");

        JsonNode root = objectMapper.readTree(env.payload());
        JsonNode scope = root.get("scope");
        assertThat(scope.get("sourceType").asText()).isEqualTo("SYS_USER_ORG");
        assertThat(scope.get("roleTypeCode").asText()).isEqualTo("ORG");
        assertThat(scope.get("treeRootExternalId").asText()).isEqualTo("1");

        JsonNode item = root.get("items").get(0);
        // api-contract.md §6.2.2.3：items.relationKey 为业务原文 ORG:2001（未 percent-encode）；
        // 仅 businessKey（任务表/sync_metadata 唯一键）按 §6.2.2.4 走 percent-encode。
        assertThat(item.get("relationKey").asText()).isEqualTo("ORG:2001");
        assertThat(item.get("subjectExternalId").asText()).isEqualTo("10001");
        assertThat(item.get("roleExternalId").asText()).isEqualTo("2001");
        assertThat(item.get("roleTypeCode").asText()).isEqualTo("ORG");
    }

    @Test
    @DisplayName("userOrgFullSync: roleTypeCode=POSITION 桶 → item.roleTypeCode=POSITION 且 relationKey 前缀固定 ORG")
    void userOrgFullSync_positionItem_yieldsPositionRoleType() throws Exception {
        SysUserOrg b = new SysUserOrg();
        b.setUserId(10002L);
        b.setOrgId(3001L);

        SyncTaskEnvelope env = builder.userOrgFullSync(1L, List.of(b),
            BATCH_KEY, "USER_ROLE",
            "POSITION", "1");

        JsonNode root = objectMapper.readTree(env.payload());
        assertThat(root.get("scope").get("roleTypeCode").asText()).isEqualTo("POSITION");

        JsonNode item = root.get("items").get(0);
        assertThat(item.get("roleTypeCode").asText()).isEqualTo("POSITION");
        // M10 修正：relationKey 前缀固定为 ORG（与 api-contract.md §6.2.2.4 对齐），
        // 无论 roleTypeCode 是 ORG 还是 POSITION
        assertThat(item.get("relationKey").asText()).isEqualTo("ORG:3001");
        assertThat(item.get("subjectExternalId").asText()).isEqualTo("10002");
        assertThat(item.get("roleExternalId").asText()).isEqualTo("3001");
    }

    @Test
    @DisplayName("userOrgFullSync: roleTypeCode 非 ORG/POSITION → IllegalArgumentException（编程契约 fail-fast）")
    void userOrgFullSync_throws_whenRoleTypeCodeInvalid() {
        SysUserOrg b = new SysUserOrg();
        b.setUserId(10001L);
        b.setOrgId(2001L);

        assertThatThrownBy(() -> builder.userOrgFullSync(1L, List.of(b),
                BATCH_KEY, "USER_ROLE", "UNKNOWN", "1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ORG or POSITION");
    }

    @Test
    @DisplayName("items 各 syncVersion.sequenceNo 严格递增")
    void items_syncVersionSequenceNoIsMonotonic() throws Exception {
        SysUser u1 = new SysUser();
        u1.setId(1L);
        u1.setStatus(1);
        SysUser u2 = new SysUser();
        u2.setId(2L);
        u2.setStatus(1);
        SysUser u3 = new SysUser();
        u3.setId(3L);
        u3.setStatus(1);

        SyncTaskEnvelope env = builder.userFullSync(1L, List.of(u1, u2, u3), BATCH_KEY, "USER_SUBJECT");
        JsonNode items = objectMapper.readTree(env.payload()).get("items");
        long s1 = items.get(0).get("syncVersion").get("sequenceNo").asLong();
        long s2 = items.get(1).get("syncVersion").get("sequenceNo").asLong();
        long s3 = items.get(2).get("syncVersion").get("sequenceNo").asLong();
        assertThat(s2).isGreaterThan(s1);
        assertThat(s3).isGreaterThan(s2);
    }
}
