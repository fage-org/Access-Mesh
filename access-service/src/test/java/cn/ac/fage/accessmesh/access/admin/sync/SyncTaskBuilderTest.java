package cn.ac.fage.accessmesh.access.admin.sync;

import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SyncTaskBuilder 单元测试。
 * <p>
 * 验证 §6.2.2.4 业务键编码、payload 字段对齐 §6.2.2.3、displayAttrs 仅放展示属性。
 * </p>
 */
class SyncTaskBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SyncSequenceProvider sequenceProvider;
    private SyncTaskBuilder builder;

    @BeforeEach
    void setUp() {
        sequenceProvider = new SyncSequenceProvider();
        builder = new SyncTaskBuilder(objectMapper, sequenceProvider);
    }

    @Test
    @DisplayName("userUpsert 生成 abstract_user + ADMIN_USER resource_entity 双 envelope")
    void userUpsert_returnsBothAbstractUserAndResourceEntity() throws Exception {
        SysUser user = new SysUser();
        user.setId(10001L);
        user.setUsername("zhangsan");
        user.setName("张三");
        user.setStatus(1);

        List<SyncTaskEnvelope> envelopes = builder.userUpsert(user);
        assertThat(envelopes).hasSize(2);

        SyncTaskEnvelope abs = envelopes.get(0);
        SyncTaskEnvelope res = envelopes.get(1);

        // syncAction
        assertThat(abs.syncAction()).isEqualTo("PERM_ABSTRACT_USER_SYNC");
        assertThat(res.syncAction()).isEqualTo("PERM_RESOURCE_ENTITY_SYNC");

        // businessKey 含 §6.2.2.4 规范字段
        assertThat(abs.businessKey()).contains("subjectTypeCode=ADMIN_USER");
        assertThat(abs.businessKey()).contains("subjectExternalId=10001");

        assertThat(res.businessKey()).contains("resourceTypeCode=ADMIN_USER");
        assertThat(res.businessKey()).contains("resourceCode=10001");
        assertThat(res.businessKey()).contains("codeType=default");

        // payload JSON 解析后含 operation=UPSERT
        JsonNode absPayload = objectMapper.readTree(abs.payload());
        assertThat(absPayload.get("operation").asText()).isEqualTo("UPSERT");
        assertThat(absPayload.get("subjectTypeCode").asText()).isEqualTo("ADMIN_USER");
        assertThat(absPayload.get("subjectExternalId").asText()).isEqualTo("10001");
        assertThat(absPayload.get("enabled").asBoolean()).isTrue();

        JsonNode resPayload = objectMapper.readTree(res.payload());
        assertThat(resPayload.get("operation").asText()).isEqualTo("UPSERT");
        assertThat(resPayload.get("resourceTypeCode").asText()).isEqualTo("ADMIN_USER");

        // payloadVersion = 1
        assertThat(abs.payloadVersion()).isEqualTo(1);
        assertThat(res.payloadVersion()).isEqualTo(1);

        // displayAttrs 不参与执行路由，仅断言其内容
        assertThat(abs.displayAttrs()).containsEntry("entityType", "abstract_user");
        assertThat(abs.displayAttrs()).containsEntry("externalId", "10001");
        assertThat(abs.displayAttrs()).containsEntry("operationType", "upsert");
    }

    @Test
    @DisplayName("userDelete 生成 DELETE 双 envelope")
    void userDelete_returnsDeleteEnvelopes() throws Exception {
        List<SyncTaskEnvelope> envelopes = builder.userDelete(10001L, "10001");
        assertThat(envelopes).hasSize(2);

        JsonNode absPayload = objectMapper.readTree(envelopes.get(0).payload());
        assertThat(absPayload.get("operation").asText()).isEqualTo("DELETE");

        JsonNode resPayload = objectMapper.readTree(envelopes.get(1).payload());
        assertThat(resPayload.get("operation").asText()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("orgUpsert 生成 abstract_role + ADMIN_ORG resource_entity 双 envelope")
    void orgUpsert_returnsBothAbstractRoleAndResourceEntity() {
        SysOrg org = new SysOrg();
        org.setId(2001L);
        org.setName("研发部");
        org.setOrgType("ORG");
        org.setStatus(1);
        org.setSortOrder(10);
        org.setParentId(1000L);

        List<SyncTaskEnvelope> envelopes = builder.orgUpsert(org);
        assertThat(envelopes).hasSize(2);

        boolean hasAbstractRole = envelopes.stream()
            .anyMatch(e -> "PERM_ABSTRACT_ROLE_SYNC".equals(e.syncAction()));
        boolean hasResourceEntity = envelopes.stream()
            .anyMatch(e -> "PERM_RESOURCE_ENTITY_SYNC".equals(e.syncAction()));
        assertThat(hasAbstractRole).isTrue();
        assertThat(hasResourceEntity).isTrue();

        SyncTaskEnvelope role = envelopes.stream()
            .filter(e -> "PERM_ABSTRACT_ROLE_SYNC".equals(e.syncAction()))
            .findFirst().orElseThrow();
        assertThat(role.businessKey()).contains("roleTypeCode=ORG");
        assertThat(role.businessKey()).contains("roleExternalId=2001");
    }

    @Test
    @DisplayName("userOrgBind: businessKey 含 percent-encoded relationKey=ORG%3A{orgId}")
    void userOrgBind_businessKeyContainsPercentEncodedRelationKey() throws Exception {
        SyncTaskEnvelope env = builder.userOrgBind(10001L, 2001L, "ORG", "ORG:2001", "1");

        assertThat(env.syncAction()).isEqualTo("PERM_USER_ROLE_SYNC");
        // relationKey 必须 percent-encoded: ORG:2001 -> ORG%3A2001
        assertThat(env.businessKey()).contains("relationKey=ORG%3A2001");
        assertThat(env.businessKey()).contains("subjectTypeCode=ADMIN_USER");
        assertThat(env.businessKey()).contains("subjectExternalId=10001");
        assertThat(env.businessKey()).contains("roleTypeCode=ORG");
        assertThat(env.businessKey()).contains("roleExternalId=2001");

        JsonNode payload = objectMapper.readTree(env.payload());
        assertThat(payload.get("operation").asText()).isEqualTo("BIND");
        assertThat(payload.get("sourceType").asText()).isEqualTo("SYS_USER_ORG");
        // relationKey in payload is the raw value (not encoded)
        assertThat(payload.get("relationKey").asText()).isEqualTo("ORG:2001");
    }

    @Test
    @DisplayName("displayAttrs 仅放 entityType/externalId/operationType")
    void displayAttrs_containsOnlyDisplayFields() {
        SysUser user = new SysUser();
        user.setId(99L);
        user.setName("test");
        user.setStatus(1);

        SyncTaskEnvelope abs = builder.userUpsert(user).get(0);

        assertThat(abs.displayAttrs()).containsOnlyKeys("entityType", "externalId", "operationType");
        assertThat(abs.displayAttrs().get("entityType")).isEqualTo("abstract_user");
        assertThat(abs.displayAttrs().get("externalId")).isEqualTo("99");
        assertThat(abs.displayAttrs().get("operationType")).isEqualTo("upsert");
    }

    @Test
    @DisplayName("syncSequenceNo 严格单调递增")
    void syncSequenceNo_strictlyMonotonic() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setName("u");
        user.setStatus(1);

        List<SyncTaskEnvelope> first = builder.userUpsert(user);
        List<SyncTaskEnvelope> second = builder.userUpsert(user);

        // each call uses sequence numbers; second batch should be strictly > first batch's max
        long firstMax = first.stream().mapToLong(SyncTaskEnvelope::syncSequenceNo).max().orElseThrow();
        long secondMin = second.stream().mapToLong(SyncTaskEnvelope::syncSequenceNo).min().orElseThrow();
        assertThat(secondMin).isGreaterThan(firstMax);
    }
}
