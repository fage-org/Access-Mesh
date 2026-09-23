package cn.ac.fage.accessmesh.access.platform.service;

import cn.ac.fage.accessmesh.access.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.bootstrap.BootstrapGraphDefinition;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 公告状态与受众生命周期组合验收（T-ADMIN-029，F010；真实 PostgreSQL + Redis，
 * 真权限引擎链——门禁不 mock，管理面经 bootstrap 五档类型级授权真实放行；
 * 自服务面 my-notices/read 走真实登录会话）。
 * <p>
 * 主链（旧实现必红）：创建=草稿 0 接收者不可见（旧实现 create 置 1 即全员可见）→
 * publish→1 仅受众可见（旧实现 my-notices 无受众过滤）→ 标已读 → revoke→2 不可读/
 * 不可标已读（旧实现无撤回端点）→ 重新发布已读延续 → 严格转换拒绝（10402）。
 * 受众语义：typed IDs JSONB 数组真实写读（旧逗号串写 JSONB 机制性失败）、
 * ALL/USER 矛盾表达拒绝、非法目标用户拒绝、ORG 值域拒绝、USER→ALL 切换
 * target_ids 真置空（UpdateEntity 显式列集）、删除级联清理已读记录。
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
    "PERM_INTERNAL_SECRET=test-internal-secret-for-notice-it",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-notice-it",
})
class NoticeLifecyclePgIT {

    private static final Long TENANT = 1L;
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-notice-it";
    private static final String SIGN_SECRET = "test-signature-secret-for-notice-it";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, NoticeLifecyclePgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AccessBootstrapInitializer initializer;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("生命周期主链：草稿不可见→发布仅受众可见→已读→撤回不可读不可标已读→重发布已读延续；严格转换拒绝")
    void noticeLifecycleWithAudienceVisibility() throws Exception {
        Fixture fx = bootstrapWithUsers("lc");
        String targetToken = loginAs(fx.targetUsername, fx.targetPassword);
        String outsiderToken = loginAs(fx.outsiderUsername, fx.outsiderPassword);

        // —— 阶段 1：USER 受众公告创建=草稿（status=0），对接收者不可见 ——
        // 旧实现 create 置 status=1（=已发布），「草稿不可见」断言必红
        ObjectNode createReq = JSON.objectNode().put("title", "定向公告").put("content", "正文")
            .put("targetType", "USER");
        createReq.putArray("targetUserIds").add(fx.targetUserId).add(fx.adminUserId);
        long noticeId = postAsAdmin("/api/access/notice/create", fx.adminUserId, createReq).asLong();
        assertThat(jdbc.queryForObject(
            "SELECT status FROM sys_notice WHERE tenant_id = ? AND id = ?", Integer.class, TENANT, noticeId))
            .as("创建必须落草稿状态 0（DDL：0=草稿）").isEqualTo(0);
        assertThat(visibleNoticeIds(targetToken))
            .as("草稿期目标受众不可见（旧实现 create 置 1 此处必红）").doesNotContain(noticeId);
        assertThat(performSelfServiceCode("/api/access/notice/read",
            JSON.objectNode().put("id", noticeId), targetToken))
            .as("草稿期标已读按不可见拒绝（10401）").isEqualTo(10401);

        // —— 阶段 2：发布后仅目标受众可见（旧实现无受众过滤，outsider 也能看到 USER 公告）——
        postAsAdmin("/api/access/notice/publish", fx.adminUserId, JSON.objectNode().put("id", noticeId), 200);
        assertThat(visibleNoticeIds(targetToken))
            .as("发布后目标受众可见").contains(noticeId);
        assertThat(visibleNoticeIds(outsiderToken))
            .as("发布后非目标受众不可见").doesNotContain(noticeId);

        // —— 阶段 3：目标受众标已读成功，isRead 回读 ——
        performSelfService("/api/access/notice/read",
            JSON.objectNode().put("id", noticeId), targetToken, 200);
        JsonNode mine = myNotices(targetToken);
        assertThat(readFlagOf(mine, noticeId)).as("标已读后 isRead=true").isTrue();

        // —— 阶段 4：撤回后不可读、不可标已读；已读记录保留 ——
        postAsAdmin("/api/access/notice/revoke", fx.adminUserId, JSON.objectNode().put("id", noticeId), 200);
        assertThat(jdbc.queryForObject(
            "SELECT status FROM sys_notice WHERE tenant_id = ? AND id = ?", Integer.class, TENANT, noticeId))
            .as("撤回必须置 2（DDL：2=已撤回）").isEqualTo(2);
        assertThat(visibleNoticeIds(targetToken))
            .as("撤回后受众不可见").doesNotContain(noticeId);
        assertThat(performSelfServiceCode("/api/access/notice/read",
            JSON.objectNode().put("id", noticeId), targetToken))
            .as("撤回后标已读拒绝（10401）").isEqualTo(10401);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_user_notice WHERE tenant_id = ? AND notice_id = ? AND user_id = ?",
            Long.class, TENANT, noticeId, fx.targetUserId))
            .as("撤回保留已读记录（重新发布已读延续的载体）").isEqualTo(1L);

        // —— 阶段 5：重新发布（2→1）可见恢复且已读延续 ——
        postAsAdmin("/api/access/notice/publish", fx.adminUserId, JSON.objectNode().put("id", noticeId), 200);
        assertThat(visibleNoticeIds(targetToken))
            .as("重新发布后受众可见恢复").contains(noticeId);
        assertThat(readFlagOf(myNotices(targetToken), noticeId))
            .as("重新发布后已读状态延续（撤回不清已读）").isTrue();

        // —— 阶段 6：严格转换拒绝（10402）——
        assertThat(performRawCode("/api/access/notice/publish", fx.adminUserId,
            JSON.objectNode().put("id", noticeId), null))
            .as("对已发布重复发布拒绝").isEqualTo(10402);

        long draftId = postAsAdmin("/api/access/notice/create", fx.adminUserId,
            JSON.objectNode().put("title", "草稿拒绝撤回").put("content", "正文"), 200).asLong();
        assertThat(performRawCode("/api/access/notice/revoke", fx.adminUserId,
            JSON.objectNode().put("id", draftId), null))
            .as("对草稿撤回拒绝（未发布无需撤回）").isEqualTo(10402);
        postAsAdmin("/api/access/notice/publish", fx.adminUserId, JSON.objectNode().put("id", draftId), 200);
        postAsAdmin("/api/access/notice/revoke", fx.adminUserId, JSON.objectNode().put("id", draftId), 200);
        assertThat(performRawCode("/api/access/notice/revoke", fx.adminUserId,
            JSON.objectNode().put("id", draftId), null))
            .as("对已撤回重复撤回拒绝").isEqualTo(10402);

        // —— 阶段 7：ALL 公告全员可见（含受众外用户）——
        long allId = postAsAdmin("/api/access/notice/create", fx.adminUserId,
            JSON.objectNode().put("title", "全员公告").put("content", "正文"), 200).asLong();
        postAsAdmin("/api/access/notice/publish", fx.adminUserId, JSON.objectNode().put("id", allId), 200);
        assertThat(visibleNoticeIds(outsiderToken))
            .as("全员公告受众外用户也可见").contains(allId);
        // ALL 公告落库不带受众数组
        assertThat(jdbc.queryForObject(
            "SELECT target_ids FROM sys_notice WHERE tenant_id = ? AND id = ?", String.class, TENANT, allId))
            .as("ALL 公告 target_ids 必须为 NULL").isNull();
    }

    @Test
    @DisplayName("受众语义矩阵：typed IDs JSONB 真写读；矛盾表达/非法目标/ORG 值域拒绝；USER→ALL 置空；删除级联；无授权管理面 403")
    void audienceSemanticsAndJsonbRoundtrip() throws Exception {
        Fixture fx = bootstrapWithUsers("as");
        String targetToken = loginAs(fx.targetUsername, fx.targetPassword);

        // —— typed IDs JSONB 数组真实写读（旧逗号串写 JSONB 机制性失败：'1,2' 非法 JSON）——
        ObjectNode createReq = JSON.objectNode().put("title", "多用户公告").put("content", "正文")
            .put("targetType", "USER");
        createReq.putArray("targetUserIds").add(fx.targetUserId).add(fx.adminUserId);
        long multiId = postAsAdmin("/api/access/notice/create", fx.adminUserId, createReq).asLong();
        assertThat(jdbc.queryForObject(
            "SELECT string_agg(e.value::text, ',' ORDER BY e.ordinality) "
                + "FROM sys_notice n, jsonb_array_elements(n.target_ids) WITH ORDINALITY AS e(value, ordinality) "
                + "WHERE n.tenant_id = ? AND n.id = ?",
            String.class, TENANT, multiId))
            .as("多用户受众必须以 JSONB 数字数组落库（元素按写入序）")
            .isEqualTo(fx.targetUserId + "," + fx.adminUserId);
        assertThat(jdbc.queryForObject(
            "SELECT jsonb_typeof(target_ids) FROM sys_notice WHERE tenant_id = ? AND id = ?",
            String.class, TENANT, multiId))
            .as("落库形态必须是 jsonb array").isEqualTo("array");
        JsonNode detail = postAsAdmin("/api/access/notice/detail", fx.adminUserId,
            JSON.objectNode().put("id", multiId), 200);
        List<Long> roundtrip = new ArrayList<>();
        detail.path("targetUserIds").forEach(i -> roundtrip.add(i.asLong()));
        assertThat(roundtrip).as("detail 读回 typed ID 数组").containsExactly(fx.targetUserId, fx.adminUserId);

        // —— 单用户受众 + my-notices 受众过滤（服务层真可见性，非门禁 403 冒充）——
        ObjectNode singleReq = JSON.objectNode().put("title", "单用户公告").put("content", "正文")
            .put("targetType", "USER");
        singleReq.putArray("targetUserIds").add(fx.targetUserId);
        long singleId = postAsAdmin("/api/access/notice/create", fx.adminUserId, singleReq).asLong();
        postAsAdmin("/api/access/notice/publish", fx.adminUserId, JSON.objectNode().put("id", singleId), 200);
        assertThat(visibleNoticeIds(targetToken)).contains(singleId);

        // —— 矛盾表达拒绝（10008）——
        ObjectNode allWithIds = JSON.objectNode().put("title", "矛盾").put("content", "正文")
            .put("targetType", "ALL");
        allWithIds.putArray("targetUserIds").add(fx.targetUserId);
        assertThat(performRawCode("/api/access/notice/create", fx.adminUserId, allWithIds, null))
            .as("ALL 带目标用户拒绝").isEqualTo(10008);
        assertThat(performRawCode("/api/access/notice/create", fx.adminUserId,
            JSON.objectNode().put("title", "空受众").put("content", "正文").put("targetType", "USER"), null))
            .as("USER 缺省目标列表拒绝").isEqualTo(10008);
        ObjectNode emptyArr = JSON.objectNode().put("title", "空数组").put("content", "正文")
            .put("targetType", "USER");
        emptyArr.putArray("targetUserIds");
        assertThat(performRawCode("/api/access/notice/create", fx.adminUserId, emptyArr, null))
            .as("USER 空数组拒绝").isEqualTo(10008);

        // —— 非法目标用户拒绝（10001：不存在/跨租户）——
        ObjectNode ghostReq = JSON.objectNode().put("title", "幽灵目标").put("content", "正文")
            .put("targetType", "USER");
        ghostReq.putArray("targetUserIds").add(99_999_999L);
        assertThat(performRawCode("/api/access/notice/create", fx.adminUserId, ghostReq, null))
            .as("不存在的目标用户拒绝").isEqualTo(10001);

        // —— ORG 值域拒绝（90001：预留未实现，DTO @Pattern）——
        ObjectNode orgReq = JSON.objectNode().put("title", "ORG 拒绝").put("content", "正文")
            .put("targetType", "ORG");
        orgReq.putArray("targetUserIds").add(fx.targetUserId);
        assertThat(performRawCode("/api/access/notice/create", fx.adminUserId, orgReq, null))
            .as("ORG 受众预留未实现必须拒绝").isEqualTo(90001);

        // —— USER→ALL 切换：target_ids 必须真置空（UpdateEntity 显式列集）——
        postAsAdmin("/api/access/notice/update", fx.adminUserId,
            JSON.objectNode().put("id", multiId).put("title", "改全员").put("content", "正文")
                .put("targetType", "ALL"), 200);
        assertThat(jdbc.queryForObject(
            "SELECT target_ids FROM sys_notice WHERE tenant_id = ? AND id = ?", String.class, TENANT, multiId))
            .as("USER→ALL 切换后 target_ids 置 NULL（update(entity) 忽略 null 列的形态必须规避）").isNull();

        // —— 删除级联清理已读记录（兑现 Controller 历来 javadoc 声称）——
        performSelfService("/api/access/notice/read",
            JSON.objectNode().put("id", singleId), targetToken, 200);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_user_notice WHERE tenant_id = ? AND notice_id = ?",
            Long.class, TENANT, singleId)).isEqualTo(1L);
        ObjectNode deleteReq = JSON.objectNode();
        deleteReq.putArray("ids").add(singleId);
        postAsAdmin("/api/access/notice/delete", fx.adminUserId, deleteReq, 200);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_user_notice WHERE tenant_id = ? AND notice_id = ?",
            Long.class, TENANT, singleId))
            .as("公告删除必须级联清理已读记录").isEqualTo(0L);

        // —— 无 ADMIN_NOTICE 授权用户管理面拒绝（服务层类型级门禁真实生效）——
        assertThat(performRawCode("/api/access/notice/create", fx.outsiderUserId,
            JSON.objectNode().put("title", "越权").put("content", "正文"), null))
            .as("无授权用户创建公告必须 403（业务码 403）").isEqualTo(403);
        assertThat(performRawCode("/api/access/notice/page", fx.outsiderUserId,
            JSON.objectNode().put("pageNum", 1).put("pageSize", 10), null))
            .as("无授权用户公告分页必须 403").isEqualTo(403);
    }

    // ===== 夹具与请求辅助（Gateway 转发形态：内部凭证 + 验签 X-User-Id；自服务面另附会话令牌） =====

    private record Fixture(long adminUserId, long targetUserId, String targetUsername, String targetPassword,
                           long outsiderUserId, String outsiderUsername, String outsiderPassword) {}

    /** 空库 bootstrap + 默认树 + 目标/受众外两个普通用户（真实登录用）。 */
    private Fixture bootstrapWithUsers(String prefix) throws Exception {
        initializer.initialize(ADMIN_PASSWORD);
        String marker = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        long rootOrgId = jdbc.queryForObject(
            "SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true AND delete_flag = 0",
            Long.class, TENANT);

        JsonNode target = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode().put("username", "nlt-target-" + marker)
                .put("name", "目标用户").put("orgId", rootOrgId));
        JsonNode outsider = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode().put("username", "nlt-outsider-" + marker)
                .put("name", "受众外用户").put("orgId", rootOrgId));
        return new Fixture(adminUserId,
            target.path("id").asLong(), "nlt-target-" + marker, target.path("initialPassword").asText(),
            outsider.path("id").asLong(), "nlt-outsider-" + marker, outsider.path("initialPassword").asText());
    }

    /** 当前登录用户可见公告 ID 列表（真实登录会话调 my-notices，操作者由会话解析）。 */
    private List<Long> visibleNoticeIds(String token) throws Exception {
        JsonNode data = myNotices(token);
        List<Long> ids = new ArrayList<>();
        data.path("items").forEach(i -> ids.add(i.path("noticeId").asLong()));
        return ids;
    }

    /**
     * 自服务请求（my-notices/read）按真实 Gateway 白名单转发形态构造：
     * InternalSecretFilter 对白名单路径仍无条件注入 X-Internal-Secret，但 HeaderEnrichFilter
     * skipAuth 不注入 X-User-Id/X-Tenant-Id、SignatureEnrichFilter 不签名（双轨评审 P0-1——
     * 旧夹具给自服务请求注入完整凭证头=非白名单路径形态，恰好绕开真实断链：密钥拦截器不豁免时
     * internalAuthenticated+无用户头落「纯服务调用」分支恒 400；豁免后走 Sa-Token 会话分支）。
     */
    private JsonNode myNotices(String token) throws Exception {
        return performSelfService("/api/access/notice/my-notices", JSON.objectNode(), token, 200);
    }

    private JsonNode performSelfService(String path, ObjectNode body, String token,
                                        int expectedEnvelopeCode) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body))
            .header("X-Internal-Secret", INTERNAL_SECRET)
            .header("Authorization", "Bearer " + token);
        MvcResult result = mockMvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode envelope = mapper.readTree(raw);
        assertThat(envelope.path("code").asInt())
            .as("自服务端点必须走会话分支可达（白名单四载体形态），path=%s，响应：%s", path, raw)
            .isEqualTo(expectedEnvelopeCode);
        return envelope.path("data");
    }

    /** 自服务负向锁用：真实 Gateway 白名单转发形态，返回业务信封码不断言。 */
    private int performSelfServiceCode(String path, ObjectNode body, String token) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body))
            .header("X-Internal-Secret", INTERNAL_SECRET)
            .header("Authorization", "Bearer " + token);
        MvcResult result = mockMvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(raw).path("code").asInt();
    }

    private Boolean readFlagOf(JsonNode myNoticesData, long noticeId) {
        for (JsonNode item : myNoticesData.path("items")) {
            if (item.path("noticeId").asLong() == noticeId) {
                return item.path("isRead").asBoolean();
            }
        }
        return null;
    }

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body) throws Exception {
        return performAndUnwrap(path, operatorUserId, body, null, 200);
    }

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body, int expectedCode)
        throws Exception {
        return performAndUnwrap(path, operatorUserId, body, null, expectedCode);
    }

    private JsonNode performAndUnwrap(String path, long operatorUserId, ObjectNode body, String token,
                                      int expectedEnvelopeCode) throws Exception {
        String raw = performRaw(path, operatorUserId, body, token);
        JsonNode envelope = mapper.readTree(raw);
        assertThat(envelope.path("code").asInt())
            .as("业务信封码必须匹配，path=%s，响应：%s", path, raw).isEqualTo(expectedEnvelopeCode);
        return envelope.path("data");
    }

    /** 负向锁用：返回业务信封码，不断言（403/错误码共用）。 */
    private int performRawCode(String path, long operatorUserId, ObjectNode body, String token) throws Exception {
        String raw = performRaw(path, operatorUserId, body, token);
        return mapper.readTree(raw).path("code").asInt();
    }

    private String performRaw(String path, long operatorUserId, ObjectNode body, String token) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String userId = String.valueOf(operatorUserId);
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body));
        commonHeaders(userId, ts).forEach(request::header);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Map<String, String> commonHeaders(String userId, long ts) throws Exception {
        return Map.of(
            "X-Internal-Secret", INTERNAL_SECRET,
            "X-Tenant-Id", String.valueOf(TENANT),
            "X-User-Id", userId,
            "X-User-Signature", hmac(userId, String.valueOf(TENANT), ts),
            "X-Signature-Timestamp", String.valueOf(ts));
    }

    /** 真实登录（MemberCandidatesGatePgIT 模式：真实 Redis 验证码 + BCrypt 密码 + Sa-Token 会话）。 */
    private String loginAs(String username, String password) throws Exception {
        String captchaId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set("captcha:" + captchaId, "3141", 5, TimeUnit.MINUTES);
        MvcResult result = mockMvc.perform(post("/api/access/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "tenantId", "1", "username", username, "password", password,
                    "captchaId", captchaId, "captchaCode", "3141", "clientId", "console"))))
            .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("code").asInt()).as("普通用户登录必须成功：%s", body).isEqualTo(200);
        return body.path("data").path("accessToken").asText();
    }

    /** Gateway 转发签名（与 SecurityMatrixIT 同算法：HmacSHA256("userId|tenantId|timestamp") 十六进制）。 */
    private static String hmac(String userId, String tenantId, long timestamp) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            SIGN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
}
