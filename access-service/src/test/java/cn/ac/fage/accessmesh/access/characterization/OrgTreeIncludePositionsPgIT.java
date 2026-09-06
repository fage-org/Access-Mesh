package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 组织树一体树与门禁裁剪特征测试（T-ADMIN-021，真实 PostgreSQL + Redis + 本地权限引擎）。
 * <p>
 * 固化：① includePositions 组织+岗位一体树（岗位挂所属组织子节点）与响应 {items:[...]}
 * 包装（P1-3）；② 岗位节点后端裁剪——仅 ORG:VIEW（无 VIEW_POSITION）的调用者响应不含
 * 任何 orgType=2 节点（否定性验收）；③ 引擎技术故障 → SystemException 统一响应业务码
 * 99999（HTTP 200），不得静默降级为裁剪后的树（P2-1 故障验收）；④ 默认 false 行为兼容
 * （orgType 必填 10107 / 单类型树）；⑤ 债务①：operationCode（CREATE 限默认树）与
 * treeConfigId（缺省=默认树子树，用户决策契约字面）；⑥ orgName 树剪枝（修正原死参数）。
 * Docker 不可用时由 Testcontainers 自动跳过（容器轨道）。
 * </p>
 */
@Tag("testcontainers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class OrgTreeIncludePositionsPgIT {

    private static final Long TENANT = 1L;
    private static final String PASSWORD = "Pass@123";

    /** ORG 资源类型值与操作位（schema 种子：CREATE=1 / VIEW=2 预置，VIEW_POSITION=512 扩展码） */
    private static final int RESOURCE_TYPE_ORG = 29;
    private static final long BIT_CREATE = 1L;
    private static final long BIT_VIEW = 2L;
    private static final long BIT_VIEW_POSITION = 512L;

    /** 固定主体/节点 id（@BeforeEach 全量清理，无跨用例冲突） */
    private static final long ROOT_ID = 9001L;
    private static final long ORG_A_ID = 9002L;
    private static final long ORG_B_ID = 9003L;
    private static final long POS_P1_ID = 9004L;
    private static final long POS_P2_ID = 9005L;
    private static final long ROOT2_ID = 9006L;
    private static final long ORG_C_ID = 9007L;
    private static final long CFG_DEFAULT_ID = 9101L;
    private static final long CFG_ALT_ID = 9102L;

    /** 每用例独立用户 id，避免跨用例主键冲突 */
    private static final AtomicLong USER_SEQ = new AtomicLong();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, OrgTreeIncludePositionsPgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 故障验收用引擎 spy：仅指定操作码注入技术故障，其余真实 */
    @SpyBean
    private cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine permQueryEngine;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM role_resource_permission WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM user_role WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM abstract_role WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM abstract_user WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM sys_user_org WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM sys_user WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM sys_org WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM sys_org_tree_config WHERE tenant_id = ?", TENANT);
        seedOrgWorld();
    }

    /** 标准组织世界：默认树 R→{A→B, P1}（P2 挂 B）+ 非默认树 R2→C + 两份树配置。 */
    private void seedOrgWorld() {
        insertOrg(ROOT_ID, "t21-root", "总部", "1", 0L, 1);
        insertOrg(ORG_A_ID, "t21-a", "研发中心", "1", ROOT_ID, 1);
        insertOrg(ORG_B_ID, "t21-b", "后端组", "1", ORG_A_ID, 1);
        insertOrg(POS_P1_ID, "t21-p1", "Java 工程师", "2", ORG_A_ID, 1);
        insertOrg(POS_P2_ID, "t21-p2", "平台工程师", "2", ORG_B_ID, 1);
        insertOrg(ROOT2_ID, "t21-root2", "外部树", "1", 0L, 1);
        insertOrg(ORG_C_ID, "t21-c", "外部子部门", "1", ROOT2_ID, 1);
        jdbc.update(
            "INSERT INTO sys_org_tree_config (id, tenant_id, root_org_id, tree_name, tree_type, is_default) "
                + "VALUES (?, ?, ?, '默认树', 'ORG', true)", CFG_DEFAULT_ID, TENANT, ROOT_ID);
        jdbc.update(
            "INSERT INTO sys_org_tree_config (id, tenant_id, root_org_id, tree_name, tree_type, is_default) "
                + "VALUES (?, ?, ?, '外部树配置', 'ORG', false)", CFG_ALT_ID, TENANT, ROOT2_ID);
    }

    private void insertOrg(long id, String code, String name, String orgType, long parentId, int status) {
        jdbc.update(
            "INSERT INTO sys_org (id, tenant_id, name, org_type, parent_id, code, level, sort_order, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, 1, 0, ?)",
            id, TENANT, name, orgType, parentId, code, status);
    }

    // ===== 数据装配（jdbc 直插用户/角色/授权，先于相关主体首次引擎调用） =====

    /** 插入仅 sys_user 的本地用户（无角色无授权 → 引擎 fail-closed 全拒）。返回 userId。 */
    private long insertUnprivilegedUser(String name) {
        long id = 921100L + USER_SEQ.incrementAndGet();
        String username = "orgtree_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO sys_user (id, tenant_id, username, password, name, status, user_type, force_reset_pwd) "
                + "VALUES (?, ?, ?, ?, ?, 1, 3, false)",
            id, TENANT, username, cn.dev33.satoken.secure.BCrypt.hashpw(PASSWORD), name);
        return id;
    }

    /** 插入 sys_user + abstract_user 但无角色无授权（引擎解析主体成功、全量拒绝——真·无授权用户）。 */
    private long insertSubjectOnlyUser(String name) {
        long userId = insertUnprivilegedUser(name);
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra) "
                + "VALUES (?, ?, 3, ?, ?, true, '{}')",
            userId, TENANT, String.valueOf(userId), name);
        return userId;
    }

    /** 插入拥有指定 ORG 操作位 scopeAll 授权的用户（一行一操作，MANUAL 单操作约束）。 */
    private long insertUserWithOrgGrants(String name, long... bits) {
        long userId = insertUnprivilegedUser(name);
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra) "
                + "VALUES (?, ?, 3, ?, ?, true, '{}')",
            userId, TENANT, String.valueOf(userId), name);
        Long roleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, "orgtree-role-" + UUID.randomUUID().toString().substring(0, 8), name + "-角色");
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, userId, roleId);
        for (long bits2 : bits) {
            jdbc.update(
                "INSERT INTO role_resource_permission "
                    + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                    + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL')",
                TENANT, roleId, bits2, RESOURCE_TYPE_ORG);
        }
        return userId;
    }

    private String usernameOf(long userId) {
        return jdbc.queryForObject("SELECT username FROM sys_user WHERE id = ? AND tenant_id = ?",
            String.class, userId, TENANT);
    }

    /** 真实验证码 + 单次登录请求，返回 accessToken。 */
    private String login(long userId) throws Exception {
        String captchaId = UUID.randomUUID().toString();
        String captchaCode = "5926";
        stringRedisTemplate.opsForValue().set("captcha:" + captchaId, captchaCode, 5, TimeUnit.MINUTES);
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "tenantId", "1", "username", usernameOf(userId), "password", PASSWORD,
                    "captchaId", captchaId, "captchaCode", captchaCode, "clientId", "console"))))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("code").asInt()).as("登录成功").isEqualTo(200);
        return body.get("data").get("accessToken").asText();
    }

    private JsonNode tree(String token, Object body) throws Exception {
        MvcResult result = mockMvc.perform(post("/org/tree")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
            .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ===== 树断言辅助 =====

    private void collectOrgTypes(JsonNode node, Set<Integer> types) {
        if (node == null) {
            return;
        }
        types.add(node.get("orgType").asInt());
        node.get("children").forEach(c -> collectOrgTypes(c, types));
    }

    private JsonNode findById(JsonNode node, long id) {
        if (node == null) {
            return null;
        }
        if (node.get("id").asLong() == id) {
            return node;
        }
        for (JsonNode c : node.get("children")) {
            JsonNode hit = findById(c, id);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** 顶层断言辅助：data.items 应为单根数组，返回根节点。 */
    private JsonNode singleRootOf(JsonNode body) {
        JsonNode items = body.get("data").get("items");
        assertThat(items.isArray()).as("响应 data.items 数组（P1-3 包装，非裸数组）").isTrue();
        assertThat(items.size()).as("配置子树裁剪后顶层为单根").isEqualTo(1);
        return items.get(0);
    }

    // ===== ① 兼容验收 + 响应包装 =====

    @Test
    @DisplayName("默认 false 行为兼容：orgType=1 单类型树、items 包装、默认树单根、无岗位")
    void defaultBehaviorCompatible() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("orgType", 1));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        JsonNode root = singleRootOf(body);
        assertThat(root.get("id").asLong()).isEqualTo(ROOT_ID);
        assertThat(root.get("orgName").asText()).isEqualTo("总部");

        Set<Integer> types = new HashSet<>();
        collectOrgTypes(root, types);
        assertThat(types).as("orgType=1 单类型树不含岗位").containsExactly(1);
        assertThat(findById(root, ORG_A_ID)).as("子组织在树内").isNotNull();
        assertThat(findById(root, ORG_C_ID)).as("非默认树节点不在默认树响应内").isNull();
    }

    @Test
    @DisplayName("缺省 orgType（includePositions 未开）→ 10107 ORG_TYPE_REQUIRED 保留")
    void orgTypeRequiredStillEnforced() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-校验用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("status", 1));
        assertThat(body.get("code").asInt()).isEqualTo(10107);
    }

    // ===== ② 一体树与岗位裁剪 =====

    @Test
    @DisplayName("includePositions=true 全权用户：岗位作为所属组织子节点返回、自身无下级")
    void mixedTreeIncludesPositionsForPrivilegedCaller() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("includePositions", true));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        JsonNode root = singleRootOf(body);

        JsonNode p1 = findById(root, POS_P1_ID);
        assertThat(p1).as("岗位 P1 出现在树内").isNotNull();
        assertThat(p1.get("orgType").asInt()).isEqualTo(2);
        assertThat(p1.get("children").isEmpty()).as("岗位自身无下级").isTrue();
        assertThat(findById(root, POS_P2_ID)).as("岗位 P2 出现在树内").isNotNull();

        JsonNode orgA = findById(root, ORG_A_ID);
        assertThat(orgA.get("children").toString()).as("P1 挂在所属组织 A 下").contains(String.valueOf(POS_P1_ID));
    }

    @Test
    @DisplayName("否定性验收：仅 ORG:VIEW（无 VIEW_POSITION）→ 响应不含任何 orgType=2 节点，组织轨完好")
    void positionsPrunedForViewOnlyCaller() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-仅VIEW用户", BIT_VIEW);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("includePositions", true));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        JsonNode root = singleRootOf(body);

        Set<Integer> types = new HashSet<>();
        collectOrgTypes(root, types);
        assertThat(types).as("裁剪后不含任何岗位节点（orgType=2）").containsExactly(1);
        assertThat(findById(root, ORG_A_ID)).as("组织轨不受裁剪影响").isNotNull();
        assertThat(findById(root, ORG_B_ID)).isNotNull();
    }

    @Test
    @DisplayName("主体投影缺失（sys_user 无 abstract_user）：组织轨门禁 403 fail-closed")
    void mixedTreeDeniedWithMissingSubjectProjection() throws Exception {
        long userId = insertUnprivilegedUser("组织树-投影缺失用户");
        String token = login(userId);

        MvcResult result = mockMvc.perform(post("/org/tree")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("includePositions", true))))
            .andExpect(status().isForbidden())
            .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("code").asInt()).isEqualTo(403);
    }

    // ===== ③ 故障验收 =====

    @Test
    @DisplayName("引擎技术故障（VIEW_POSITION 判定）→ SystemException 业务码 99999，不得返回裁剪后的树")
    void engineFailureFailsClosedWithBusinessCode() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-故障用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        doAnswer(inv -> {
            String op = inv.getArgument(4);
            if ("VIEW_POSITION".equals(op)) {
                throw new RuntimeException("simulated engine db failure");
            }
            return inv.callRealMethod();
        }).when(permQueryEngine).hasPermissionByCode(anyLong(), anyLong(), anyString(), any(), anyString());

        JsonNode body = tree(token, Map.of("includePositions", true));
        assertThat(body.get("code").asInt())
            .as("技术故障以统一响应业务码标识（handleSystemException 无 @ResponseStatus → HTTP 200）")
            .isEqualTo(99999);
        assertThat(body.get("data").isNull())
            .as("故障不得返回裁剪后的树（JSON null，而非 NullNode 误判）").isTrue();
    }

    // ===== ④ 债务①：operationCode / treeConfigId =====

    @Test
    @DisplayName("operationCode=CREATE：门禁按 ORG:CREATE、限默认树（treeConfigId 同时传 → 10008）")
    void createSemanticsForcedDefaultTree() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-CREATE用户", BIT_VIEW, BIT_CREATE);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("orgType", 1, "operationCode", "CREATE"));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        JsonNode root = singleRootOf(body);
        assertThat(root.get("id").asLong()).isEqualTo(ROOT_ID);

        JsonNode conflict = tree(token, Map.of("orgType", 1, "operationCode", "CREATE",
            "treeConfigId", CFG_DEFAULT_ID));
        assertThat(conflict.get("code").asInt())
            .as("CREATE 限默认树，禁止传 treeConfigId（T-ADMIN-021 用户决策）").isEqualTo(10008);

        JsonNode mixed = tree(token, Map.of("includePositions", true, "operationCode", "CREATE"));
        assertThat(mixed.get("code").asInt())
            .as("CREATE+混合树语义互斥（P2-1）").isEqualTo(10008);

        JsonNode illegal = tree(token, Map.of("orgType", 1, "operationCode", "SYNC"));
        assertThat(illegal.get("code").asInt()).as("非法 operationCode fail-closed").isEqualTo(10008);
    }

    @Test
    @DisplayName("operationCode=CREATE 无 ORG:CREATE 授权 → 403（门禁真实生效）")
    void createSemanticsEnforcesCreateGate() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-仅VIEW用户", BIT_VIEW);
        String token = login(userId);

        MvcResult result = mockMvc.perform(post("/org/tree")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("orgType", 1, "operationCode", "CREATE"))))
            .andExpect(status().isForbidden())
            .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("treeConfigId：显式传非默认配置 → 该配置子树；缺省 → 默认树子树（契约字面）；不存在 → 11001")
    void treeConfigScoping() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode alt = tree(token, Map.of("orgType", 1, "treeConfigId", CFG_ALT_ID));
        assertThat(alt.get("code").asInt()).isEqualTo(200);
        JsonNode altRoot = singleRootOf(alt);
        assertThat(altRoot.get("id").asLong()).isEqualTo(ROOT2_ID);
        assertThat(findById(altRoot, ORG_C_ID)).isNotNull();
        assertThat(findById(altRoot, ORG_A_ID)).as("默认树节点不在非默认配置子树内").isNull();

        JsonNode def = tree(token, Map.of("orgType", 1));
        assertThat(findById(singleRootOf(def), ORG_C_ID))
            .as("缺省 treeConfigId 只见默认树（契约字面，用户决策）").isNull();

        JsonNode missing = tree(token, Map.of("orgType", 1, "treeConfigId", 999999L));
        assertThat(missing.get("code").asInt()).isEqualTo(11001);
    }

    @Test
    @DisplayName("无默认树配置且未传 treeConfigId → 11001 fail-closed（禁止静默回退全量）")
    void missingDefaultConfigFailsClosed() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);
        jdbc.update("DELETE FROM sys_org_tree_config WHERE id = ?", CFG_DEFAULT_ID);

        JsonNode body = tree(token, Map.of("orgType", 1));
        assertThat(body.get("code").asInt())
            .as("无默认配置 fail-closed（对齐 resolver 禁止 fallback 既有口径）").isEqualTo(11001);
    }

    // ===== ⑤ orgName 剪枝 / parentOrgId 子树 =====

    @Test
    @DisplayName("orgName 剪枝：命中节点保留祖先链、无命中分支剪除（修正原死参数）")
    void orgNamePrunesBranches() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("includePositions", true, "orgName", "后端组"));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        JsonNode root = singleRootOf(body);
        assertThat(findById(root, ORG_B_ID)).as("命中节点保留").isNotNull();
        assertThat(findById(root, ORG_A_ID)).as("祖先链保留（避免整支消失）").isNotNull();
        assertThat(findById(root, POS_P1_ID)).as("无命中分支剪除").isNull();
        assertThat(findById(root, POS_P2_ID)).as("命中节点的子节点不因父命中而保留").isNull();
    }

    @Test
    @DisplayName("parentOrgId 子树：以该节点为顶层；不在配置子树内 → 空结果（过滤语义）")
    void parentOrgIdSubtreePivot() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("includePositions", true, "parentOrgId", ORG_A_ID));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        JsonNode items = body.get("data").get("items");
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("id").asLong()).isEqualTo(ORG_A_ID);
        assertThat(findById(items.get(0), POS_P1_ID)).as("子树内直属岗位保留").isNotNull();
        assertThat(findById(items.get(0), ORG_B_ID)).as("子树内后代组织保留").isNotNull();
        assertThat(findById(items.get(0), POS_P2_ID)).as("子树内深层岗位保留").isNotNull();

        JsonNode outside = tree(token, Map.of("orgType", 1, "parentOrgId", ROOT2_ID));
        assertThat(outside.get("code").asInt()).isEqualTo(200);
        assertThat(outside.get("data").get("items").size())
            .as("默认树范围内取非默认树节点 → 空结果").isZero();
    }

    // ===== ⑥ 过滤语义与守卫边界（根存在性守卫基于未过滤全量；orgType/status 为节点级内存过滤） =====

    @Test
    @DisplayName("status=0（根启用）：节点级过滤不误报 11002——根被滤即空树，parentOrgId 透视可见停用节点")
    void statusFilterIsNodeLevelInMemory() throws Exception {
        long disabledOrgId = 9008L;
        insertOrg(disabledOrgId, "t21-disabled", "停用部门", "1", ORG_A_ID, 0);
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("orgType", 1, "status", 0));
        assertThat(body.get("code").asInt())
            .as("启用根不匹配 status=0 → 空树（过滤语义），不得误报 11002").isEqualTo(200);
        assertThat(body.get("data").get("items").size()).isZero();

        JsonNode pivot = tree(token, Map.of("orgType", 1, "status", 0, "parentOrgId", disabledOrgId));
        assertThat(pivot.get("code").asInt()).isEqualTo(200);
        assertThat(pivot.get("data").get("items").size()).isEqualTo(1);
        assertThat(pivot.get("data").get("items").get(0).get("id").asLong())
            .as("parentOrgId 透视可取到停用节点").isEqualTo(disabledOrgId);
    }

    @Test
    @DisplayName("orgType=2 无 parentOrgId：恒空树（根为组织被类型过滤剔除），不得报 11002")
    void orgType2WithoutPivotIsEmptyTree() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("orgType", 2));
        assertThat(body.get("code").asInt())
            .as("orgType=2 单类型树为已知边界退化（空树），非配置漂移错误").isEqualTo(200);
        assertThat(body.get("data").get("items").size()).isZero();
    }

    @Test
    @DisplayName("orgType 白名单 {1,2}：非法值 10008；operationCode 大小写敏感（view 小写拒绝）")
    void orgTypeWhitelistAndOperationCodeCase() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        assertThat(tree(token, Map.of("orgType", 3)).get("code").asInt())
            .as("orgType=3 fail-closed").isEqualTo(10008);
        assertThat(tree(token, Map.of("orgType", 1, "operationCode", "view")).get("code").asInt())
            .as("operationCode 大小写敏感").isEqualTo(10008);
    }

    @Test
    @DisplayName("根组织真被软删（配置漂移）→ 11002 fail-closed")
    void softDeletedRootFailsClosedWith11002() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);
        jdbc.update("UPDATE sys_org SET delete_flag = id WHERE id = ? AND tenant_id = ?", ROOT_ID, TENANT);

        JsonNode body = tree(token, Map.of("orgType", 1));
        assertThat(body.get("code").asInt())
            .as("根行缺失是真实配置漂移，11002 而非空树").isEqualTo(11002);
    }

    @Test
    @DisplayName("岗位裁剪不可被 orgName 旁路：仅 VIEW 用户搜岗位名 → 仍零 orgType=2 节点")
    void pruningCannotBeBypassedByNameFilter() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-仅VIEW用户", BIT_VIEW);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("includePositions", true, "orgName", "Java 工程师"));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        assertThat(body.get("data").get("items").size())
            .as("名称命中岗位但其父组织名不匹配 → 整支不保留（裁剪先于名称剪枝）").isZero();
    }

    @Test
    @DisplayName("岗位裁剪不可被 parentOrgId 旁路：仅 VIEW 用户以岗位 id 透视 → 空结果")
    void pruningCannotBeBypassedByParentOrgIdPivot() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-仅VIEW用户", BIT_VIEW);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("includePositions", true, "parentOrgId", POS_P1_ID));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        assertThat(body.get("data").get("items").size())
            .as("岗位节点被裁剪后其 id 不在集合内 → 空结果（与不存在同形，无存在性 oracle）").isZero();
    }

    // ===== ⑦ 门禁路径区分与剪枝组合 =====

    @Test
    @DisplayName("主体存在但无任何授权（引擎成功响应明确拒绝）：组织轨门禁 403 fail-closed")
    void mixedTreeDeniedWithSubjectButNoGrants() throws Exception {
        long userId = insertSubjectOnlyUser("组织树-主体无授权用户");
        String token = login(userId);

        MvcResult result = mockMvc.perform(post("/org/tree")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("includePositions", true))))
            .andExpect(status().isForbidden())
            .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("组合：status=0 + parentOrgId + orgName 同时命中停用节点 → 透视结果不被误删")
    void combinedFiltersKeepPivotNodeMatchingName() throws Exception {
        long disabledOrgId = 9009L;
        insertOrg(disabledOrgId, "t21-disabled2", "停用部门", "1", ORG_A_ID, 0);
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of(
            "orgType", 1, "status", 0, "parentOrgId", disabledOrgId, "orgName", "停用部门"));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        assertThat(body.get("data").get("items").size())
            .as("透视节点自身命中名称且满足 status——不得因祖先被 status 过滤断链而误删").isEqualTo(1);
        assertThat(body.get("data").get("items").get(0).get("id").asLong()).isEqualTo(disabledOrgId);
    }

    @Test
    @DisplayName("orgName 全不命中：顶层被剪除 → 空结果（不返回裸根骨架）")
    void keywordMissYieldsEmptyResult() throws Exception {
        long userId = insertUserWithOrgGrants("组织树-全权用户", BIT_VIEW, BIT_VIEW_POSITION);
        String token = login(userId);

        JsonNode body = tree(token, Map.of("orgType", 1, "orgName", "不存在的名字"));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        assertThat(body.get("data").get("items").size()).isZero();
    }
}
