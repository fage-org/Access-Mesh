package cn.ac.fage.accessmesh.admin.sync.contract;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.sync.SyncSequenceProvider;
import cn.ac.fage.accessmesh.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 防回归契约测试：
 * <p>
 * 验证 {@link SyncTaskBuilder} 生成的 envelope.payload（JSON 字符串）反序列化后，
 * {@code extra} 字段为 {@code Map<String,Object>}（即 JSON 对象），而不是嵌套的
 * JSON 字符串。这是 P1 修复（{@code extra:String} → {@code extra:Map<String,Object>}）
 * 的入口契约：admin-service 直传 Map，permission-center 端 DTO 也是 Map。
 * </p>
 * <p>
 * 反序列化目标使用 {@code Map<String,Object>}（与 {@code AbstractPermSyncHandler.readPayload}
 * 保持一致）—— admin-service 不引用 permission-center 的 sync DTO 类，因此这里
 * 通过 Map 形态直接断言 JSON 结构。
 * </p>
 */
class SyncPayloadContractTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SyncTaskBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SyncTaskBuilder(objectMapper, new SyncSequenceProvider());
    }

    @Test
    @DisplayName("abstract_user envelope payload extra 反序列化为 Map<String,Object>")
    void abstractUserPayload_extraIsMap() throws Exception {
        SysUser user = new SysUser();
        user.setId(10001L);
        user.setUsername("zhangsan");
        user.setName("张三");
        user.setStatus(1);

        List<SyncTaskEnvelope> envelopes = builder.userUpsert(user);
        SyncTaskEnvelope abs = envelopes.get(0);

        Map<String, Object> payload = objectMapper.readValue(abs.payload(), MAP_TYPE);
        Object extra = payload.get("extra");

        assertThat(extra)
                .as("extra MUST be a JSON object (Map), not a stringified JSON")
                .isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> extraMap = (Map<String, Object>) extra;
        assertThat(extraMap).containsEntry("username", "zhangsan");
        assertThat(payload.get("subjectTypeCode")).isEqualTo("ADMIN_USER");
        assertThat(payload.get("subjectExternalId")).isEqualTo("10001");
        assertThat(payload.get("operation")).isEqualTo("UPSERT");
    }

    @Test
    @DisplayName("abstract_role(orgUpsert) envelope payload extra 反序列化为 Map<String,Object>")
    void abstractRolePayload_extraIsMap() throws Exception {
        SysOrg org = new SysOrg();
        org.setId(2001L);
        org.setName("研发部");
        org.setStatus(1);
        org.setSortOrder(0);
        org.setOrgType("ORG");

        List<SyncTaskEnvelope> envelopes = builder.orgUpsert(org);
        SyncTaskEnvelope abs = envelopes.get(0); // abstract_role

        Map<String, Object> payload = objectMapper.readValue(abs.payload(), MAP_TYPE);
        Object extra = payload.get("extra");

        assertThat(extra).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> extraMap = (Map<String, Object>) extra;
        assertThat(extraMap).containsEntry("orgType", "ORG");
        assertThat(payload.get("roleTypeCode")).isEqualTo("ORG");
        assertThat(payload.get("roleExternalId")).isEqualTo("2001");
        assertThat(payload.get("operation")).isEqualTo("UPSERT");
    }

    @Test
    @DisplayName("user_role envelope payload 不含 extra 字段（DTO 也无此字段）")
    void userRolePayload_hasNoExtra() throws Exception {
        SyncTaskEnvelope env = builder.userOrgBind(10001L, 2001L, "ORG", "ORG:2001", "1");

        Map<String, Object> payload = objectMapper.readValue(env.payload(), MAP_TYPE);

        assertThat(payload).doesNotContainKey("extra");
        assertThat(payload.get("subjectTypeCode")).isEqualTo("ADMIN_USER");
        assertThat(payload.get("subjectExternalId")).isEqualTo("10001");
        assertThat(payload.get("roleTypeCode")).isEqualTo("ORG");
        assertThat(payload.get("roleExternalId")).isEqualTo("2001");
        assertThat(payload.get("relationKey")).isEqualTo("ORG:2001");
        assertThat(payload.get("operation")).isEqualTo("BIND");
    }

    @Test
    @DisplayName("resource_entity(menuUpsert) envelope payload extra 反序列化为 Map<String,Object>")
    void resourceEntityPayload_extraIsMap() throws Exception {
        SysMenu menu = new SysMenu();
        menu.setId(3001L);
        menu.setName("用户管理");
        menu.setStatus(1);
        menu.setSortOrder(10);
        menu.setMenuType("MENU");
        menu.setPermCode("ADMIN_USER:VIEW");
        menu.setPath("/user");
        menu.setComponent("UserView");
        menu.setIcon("user");
        menu.setVisible(Boolean.TRUE);

        SyncTaskEnvelope env = builder.menuUpsert(menu);

        Map<String, Object> payload = objectMapper.readValue(env.payload(), MAP_TYPE);
        Object extra = payload.get("extra");

        assertThat(extra).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> extraMap = (Map<String, Object>) extra;
        assertThat(extraMap).containsEntry("menuType", "MENU");
        assertThat(extraMap).containsEntry("permCode", "ADMIN_USER:VIEW");
        assertThat(extraMap).containsEntry("path", "/user");
        assertThat(extraMap).containsEntry("component", "UserView");
        assertThat(extraMap).containsEntry("icon", "user");
        assertThat(extraMap).containsEntry("visible", Boolean.TRUE);
        assertThat(payload.get("resourceTypeCode")).isEqualTo("ADMIN_MENU");
        assertThat(payload.get("resourceCode")).isEqualTo("3001");
        assertThat(payload.get("operation")).isEqualTo("UPSERT");
    }
}
