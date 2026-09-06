package cn.ac.fage.accessmesh.access.characterization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件服务安全特征测试（T-ADMIN-023，真实 PostgreSQL + Redis + 本地磁盘存储根）。
 * <p>
 * 固化三项安全语义：① detail/page/download 补 ADMIN_FILE 类型级 VIEW 门禁
 * （无授权用户 HTTP 403 fail-closed，有 scopeAll 授权用户放行）；② 统一路径安全函数
 * （DB filePath 被 ../ 污染时下载拒绝 10506，纵深防御）；③ 删除顺序反转
 * （先同事务软删元数据、提交后物理清理；清理失败保留孤儿文件、不影响已提交软删）。
 * 存储根指向测试临时目录（单实例本地盘语义）。
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
class FileServiceSecurityPgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");
    private static final String PASSWORD = "Pass@123";
    /** 文件存储根（单实例本地盘语义，测试临时目录；static 先于 @DynamicPropertySource 注册） */
    private static final Path STORAGE_ROOT = createStorageRoot();

    /** ADMIN_FILE 资源类型值与操作位（schema 种子：CREATE=1/VIEW=2/DELETE=8，resource_type=25） */
    private static final int RESOURCE_TYPE_ADMIN_FILE = 25;
    private static final long BIT_CREATE = 1L;
    private static final long BIT_VIEW = 2L;
    private static final long BIT_DELETE = 8L;

    /** Redis 容器与客户端密码必须对齐（同 LoginLockTemporaryPgIT） */
    private static final String REDIS_TEST_PASSWORD = "accessmesh-test";

    /** 每用例独立用户 id，避免跨用例主键冲突 */
    private static final AtomicLong USER_SEQ = new AtomicLong();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("file_security_test")
        .withUsername("perm")
        .withPassword("perm");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD)
        .withExposedPorts(6379);

    /**
     * Testcontainers 的 getJdbcUrl() 已自带查询参数，直接追加 "?stringtype=unspecified"
     * 会并入前一个参数值被 pgjdbc 静默忽略，按是否已含 "?" 选择分隔符
     * （T-ADMIN-026 订正；先例 KeywordLikeSearchPgIT.urlWithStringtype）。
     */
    static String urlWithStringtype() {
        String url = postgres.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified";
    }


    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> urlWithStringtype());
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
        registry.add("file.storage.path", () -> STORAGE_ROOT.toString());
    }

    @BeforeAll
    static void setupSchema() throws Exception {
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            urlWithStringtype(), postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /** 测试结束递归清理临时存储根，避免每次运行泄漏系统临时目录 */
    @AfterAll
    static void cleanupStorageRoot() throws Exception {
        try (var walk = Files.walk(STORAGE_ROOT)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (java.io.IOException e) {
                    // 尽力清理，失败不影响测试结果
                }
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("accessmesh-file-it");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法创建文件存储根临时目录", e);
        }
    }

    // ===== 数据装配（jdbc 直插事实/授权，先于相关主体首次引擎调用） =====

    /** 插入仅 sys_user 的本地用户（无角色无授权 → 引擎 fail-closed 全拒）。返回 userId。 */
    private long insertUnprivilegedUser(String name) {
        long id = 923300L + USER_SEQ.incrementAndGet();
        String username = "filesec_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO sys_user (id, tenant_id, username, password, name, status, user_type, force_reset_pwd) "
                + "VALUES (?, ?, ?, ?, ?, 1, 3, false)",
            id, TENANT, username, cn.dev33.satoken.secure.BCrypt.hashpw(PASSWORD), name);
        return id;
    }

    /**
     * 插入拥有 ADMIN_FILE 全链路授权（scopeAll CREATE/VIEW/DELETE）的用户：
     * sys_user + abstract_user（LOCAL_USER 投影，T-ORG-001 同值 id 不变量）
     * + BASIC_ROLE + user_role 绑定 + 3 条 scopeAll 授权（一行一操作，MANUAL 单操作约束）。
     */
    private long insertFileAdminUser(String name) {
        long userId = insertUnprivilegedUser(name);
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra) "
                + "VALUES (?, ?, 3, ?, ?, true, '{}')",
            userId, TENANT, String.valueOf(userId), name);
        Long roleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, "filesec-role-" + UUID.randomUUID().toString().substring(0, 8), name + "-角色");
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, userId, roleId);
        for (long bits : new long[] {BIT_CREATE, BIT_VIEW, BIT_DELETE}) {
            jdbc.update(
                "INSERT INTO role_resource_permission "
                    + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                    + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL')",
                TENANT, roleId, bits, RESOURCE_TYPE_ADMIN_FILE);
        }
        return userId;
    }

    private String usernameOf(long userId) {
        return jdbc.queryForObject(
            "SELECT username FROM sys_user WHERE id = ? AND tenant_id = ?", String.class, userId, TENANT);
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

    /** 直插 sys_file 元数据行（物理文件按需手工布置），返回文件 id。 */
    private long insertFileRow(String originalName, String filePath, String bucketName) {
        return jdbc.queryForObject(
            "INSERT INTO sys_file (tenant_id, original_name, file_name, file_path, file_url, file_size, "
                + "file_type, bucket_name, created_by, created_at, updated_at, delete_flag) "
                + "VALUES (?, ?, ?, ?, ?, 3, 'text/plain', ?, 0, now(), now(), 0) RETURNING id",
            Long.class, TENANT, originalName, "uuid.txt", filePath, "/files/" + filePath, bucketName);
    }

    /**
     * 插入 ADMIN_FILE 文件夹投影（resource_entity，code=folder，幂等复用已有行），返回实体 id。
     * 模拟 bootstrap 预置/上传惰性登记产出的事实行（owner=access-service）。
     */
    private long insertFolderProjection(String folderCode) {
        List<Long> existing = jdbc.queryForList(
            "SELECT id FROM resource_entity WHERE tenant_id = ? AND resource_type = ? "
                + "AND code = ? AND code_type = 'default' AND delete_flag = 0",
            Long.class, TENANT, RESOURCE_TYPE_ADMIN_FILE, folderCode);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return jdbc.queryForObject(
            "INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status, "
                + "owner_service_code, maintain_source, delete_flag) "
                + "VALUES (?, ?, ?, 'default', ?, 1, 'access-service', 'MANUAL', 0) RETURNING id",
            Long.class, TENANT, RESOURCE_TYPE_ADMIN_FILE, folderCode, folderCode);
    }

    /**
     * 插入仅持指定文件夹实例授权的用户（无类型级/scopeAll 授权）：
     * folderOpBits 每项 = 文件夹编码 → 该文件夹上的操作位数列表（一行一操作，MANUAL 单操作约束）。
     */
    private long insertFolderUser(String name, Map<String, List<Long>> folderOpBits) {
        long userId = insertUnprivilegedUser(name);
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra) "
                + "VALUES (?, ?, 3, ?, ?, true, '{}')",
            userId, TENANT, String.valueOf(userId), name);
        Long roleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, "folder-role-" + UUID.randomUUID().toString().substring(0, 8), name + "-角色");
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, userId, roleId);
        for (Map.Entry<String, List<Long>> entry : folderOpBits.entrySet()) {
            long folderEntityId = insertFolderProjection(entry.getKey());
            for (long bits : entry.getValue()) {
                jdbc.update(
                    "INSERT INTO role_resource_permission "
                        + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                        + "VALUES (?, ?, ?, ?, ?, false, 'MANUAL')",
                    TENANT, roleId, folderEntityId, bits, RESOURCE_TYPE_ADMIN_FILE);
            }
        }
        return userId;
    }

    /** ADMIN_FILE 文件夹投影有效行数（code 维度，惰性登记/预置断言用）。 */
    private long countFolderProjection(String folderCode) {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM resource_entity WHERE tenant_id = ? AND resource_type = ? "
                + "AND code = ? AND code_type = 'default' AND delete_flag = 0",
            Integer.class, TENANT, RESOURCE_TYPE_ADMIN_FILE, folderCode);
        return count == null ? 0 : count;
    }

    /** 上传真实文件（multipart），返回响应信封。 */
    private JsonNode upload(String token, String filename, String contentType, String bizType) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/file/upload")
                .file(new MockMultipartFile("file", filename, contentType, "content".getBytes()))
                .param("bizType", bizType)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode postJson(String token, String path, Object body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
            .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ===== ① VIEW 门禁 =====

    @Test
    @DisplayName("无 ADMIN_FILE:VIEW 授权：detail/download 403 fail-closed；page 过滤语义返回 200 空页（T-ADMIN-025）")
    void viewEndpointsDeniedWithoutGrant() throws Exception {
        long userId = insertUnprivilegedUser("文件安全-无权用户");
        String token = login(userId);
        // 门禁键=文件所属文件夹（元数据先取行）：须用真实存在的文件行，不存在会先走 10501 业务错
        long existingFile = insertFileRow("无权可见.txt", "vg-denied/2026/09/06/n.txt", "vg-denied");

        // detail/download 按文件所属文件夹实例级判定（无授权 403）
        Map<String, String> deniedCases = Map.of(
            "/file/detail", "{\"id\":" + existingFile + "}",
            "/file/download", "{\"id\":" + existingFile + "}");
        for (Map.Entry<String, String> c : deniedCases.entrySet()) {
            MvcResult result = mockMvc.perform(post(c.getKey())
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(c.getValue()))
                .andExpect(status().isForbidden())
                .andReturn();
            JsonNode body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(body.get("code").asInt()).as("%s 应 403 拒绝", c.getKey()).isEqualTo(403);
        }

        // page 过滤语义不是门禁：bizType 收窄到无文件的唯一 bucket → 空全集直接空页（不触发主体判定）；
        // 「有文件但全部无权 → 空页」由 pageBizTypeOfDeniedFolderReturnsEmptySilently 用有主体用户覆盖
        JsonNode page = postJson(token, "/file/page", Map.of("bizType", "vg-empty"));
        assertThat(page.get("code").asInt()).as("page 过滤语义返回空页而非 403").isEqualTo(200);
        assertThat(page.get("data").get("total").asLong()).isZero();
        assertThat(page.get("data").get("items").size()).isZero();
    }

    @Test
    @DisplayName("scopeAll ADMIN_FILE 授权：upload→detail→download 全链路放行")
    void viewEndpointsAllowedWithScopeAllGrant() throws Exception {
        long userId = insertFileAdminUser("文件安全-授权用户");
        String token = login(userId);

        JsonNode uploaded = upload(token, "a.txt", "text/plain", "default");
        assertThat(uploaded.get("code").asInt()).isEqualTo(200);
        long fileId = uploaded.get("data").asLong();

        JsonNode detail = postJson(token, "/file/detail", Map.of("id", fileId));
        assertThat(detail.get("code").asInt()).isEqualTo(200);
        assertThat(detail.get("data").get("originalName").asText()).isEqualTo("a.txt");

        MvcResult download = mockMvc.perform(post("/file/download")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("id", fileId))))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo("content".getBytes());
    }

    // ===== ② 统一路径安全函数 =====

    @Test
    @DisplayName("DB filePath 路径穿越（../evil.txt）：download 拒绝 10506，纵深防御")
    void downloadRejectsTraversalFilePathFromDb() throws Exception {
        long userId = insertFileAdminUser("文件安全-穿越用例");
        String token = login(userId);
        long fileId = insertFileRow("evil.txt", "../evil.txt", "default");

        JsonNode body = postJson(token, "/file/download", Map.of("id", fileId));
        assertThat(body.get("code").asInt()).isEqualTo(10506);
        assertThat(body.get("message").asText()).contains("文件路径非法");
    }

    @Test
    @DisplayName("上传 bizType 路径注入（../x）：拒绝 10506 且不落盘")
    void uploadRejectsTraversalBizType() throws Exception {
        long userId = insertFileAdminUser("文件安全-bizType注入");
        String token = login(userId);
        Integer countBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_file WHERE tenant_id = ? AND delete_flag = 0", Integer.class, TENANT);

        JsonNode body = upload(token, "a.txt", "text/plain", "../x");
        assertThat(body.get("code").asInt()).isEqualTo(10506);

        Integer countAfter = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_file WHERE tenant_id = ? AND delete_flag = 0", Integer.class, TENANT);
        assertThat(countAfter).as("非法 bizType 不落任何元数据").isEqualTo(countBefore);
    }

    // ===== ③ 删除顺序反转 =====

    @Test
    @DisplayName("正常删除：软删提交 + 提交后物理清理")
    void deleteSoftDeletesThenCleansPhysicalFile() throws Exception {
        long userId = insertFileAdminUser("文件安全-正常删除");
        String token = login(userId);

        JsonNode uploaded = upload(token, "a.txt", "text/plain", "default");
        long fileId = uploaded.get("data").asLong();
        String filePath = jdbc.queryForObject(
            "SELECT file_path FROM sys_file WHERE id = ?", String.class, fileId);
        Path physical = STORAGE_ROOT.resolve(filePath);
        assertThat(Files.exists(physical)).as("上传后物理文件存在").isTrue();

        JsonNode deleted = postJson(token, "/file/delete", Map.of("ids", List.of(fileId)));
        assertThat(deleted.get("code").asInt()).isEqualTo(200);

        Long deleteFlag = jdbc.queryForObject(
            "SELECT delete_flag FROM sys_file WHERE id = ?", Long.class, fileId);
        assertThat(deleteFlag).as("元数据软删（delete_flag=id）").isEqualTo(fileId);
        assertThat(Files.exists(physical)).as("提交后物理文件已清理").isFalse();
    }

    @Test
    @DisplayName("清理失败容忍孤儿：非空目录清不掉 → 软删已提交、孤儿保留、接口仍成功")
    void deleteToleratesPhysicalCleanupFailure() throws Exception {
        long userId = insertFileAdminUser("文件安全-孤儿容忍");
        String token = login(userId);

        // DB filePath 指向手工布置的非空目录：Files.deleteIfExists 抛 DirectoryNotEmptyException
        Path orphanDir = STORAGE_ROOT.resolve("orphan/dir");
        Files.createDirectories(orphanDir);
        Files.writeString(orphanDir.resolve("child.txt"), "x");
        long fileId = insertFileRow("orphan.txt", "orphan/dir", "orphan");

        JsonNode deleted = postJson(token, "/file/delete", Map.of("ids", List.of(fileId)));
        assertThat(deleted.get("code").asInt())
            .as("物理清理失败不回滚已提交软删、不使接口失败").isEqualTo(200);

        Long deleteFlag = jdbc.queryForObject(
            "SELECT delete_flag FROM sys_file WHERE id = ?", Long.class, fileId);
        assertThat(deleteFlag).as("软删已提交（delete_flag=id）").isEqualTo(fileId);
        assertThat(Files.exists(orphanDir)).as("孤儿文件保留（优于丢失/回滚）").isTrue();
    }

    // ===== ④ 文件夹级授权（T-ADMIN-025：bizType 即文件夹实例，全链路 CREATE/VIEW/DELETE） =====

    @Test
    @DisplayName("文件夹 VIEW 实例授权：page 只见本文件夹、detail 本夹放行他夹 403、脏桶不外泄")
    void folderViewGrantIsolatesFolders() throws Exception {
        // 用例内唯一 bucket 名：类内共享库（单容器），公共 bucket 会被其他用例的文件/惰性登记污染断言
        long userId = insertFolderUser("文件夹-仅本夹VIEW", Map.of("fv-mine", List.of(BIT_VIEW)));
        String token = login(userId);
        long mineFile = insertFileRow("本夹.png", "fv-mine/2026/09/06/a.png", "fv-mine");
        long otherFile = insertFileRow("他夹.pdf", "fv-other/2026/09/06/r.pdf", "fv-other");
        // 白名单前落库的历史脏桶（无投影）——page 过滤语义下不可见（fail-closed 落入不可见侧）
        insertFileRow("脏桶.txt", "../fv-legacy/x.txt", "../fv-legacy");

        JsonNode page = postJson(token, "/file/page", Map.of());
        assertThat(page.get("code").asInt()).isEqualTo(200);
        assertThat(page.get("data").get("total").asLong()).as("只见本文件夹文件").isEqualTo(1L);
        assertThat(page.get("data").get("items").get(0).get("id").asLong()).isEqualTo(mineFile);

        assertThat(postJson(token, "/file/detail", Map.of("id", mineFile)).get("code").asInt())
            .as("本文件夹 detail 放行").isEqualTo(200);

        MvcResult denied = mockMvc.perform(post("/file/detail")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("id", otherFile))))
            .andExpect(status().isForbidden())
            .andReturn();
        assertThat(mapper.readTree(denied.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("code").asInt()).as("他文件夹 detail 403").isEqualTo(403);
    }

    @Test
    @DisplayName("page bizType 指向无权文件夹 → 静默空页（过滤语义非 403）")
    void pageBizTypeOfDeniedFolderReturnsEmptySilently() throws Exception {
        long userId = insertFolderUser("文件夹-空页用户", Map.of("pv-mine", List.of(BIT_VIEW)));
        String token = login(userId);
        insertFileRow("报告.pdf", "pv-other/2026/09/06/r.pdf", "pv-other");

        JsonNode page = postJson(token, "/file/page", Map.of("bizType", "pv-other"));
        assertThat(page.get("code").asInt()).as("无权文件夹过滤为空页而非 403").isEqualTo(200);
        assertThat(page.get("data").get("total").asLong()).isZero();
        assertThat(page.get("data").get("items").size()).isZero();
    }

    @Test
    @DisplayName("upload：本夹实例 CREATE 放行；无投影新文件夹对无类型级授权者 fail-closed 403 且不留痕")
    void uploadGatesOnTargetFolderInstance() throws Exception {
        long userId = insertFolderUser("文件夹-仅本夹CREATE", Map.of("ug-mine", List.of(BIT_CREATE)));
        String token = login(userId);

        JsonNode uploaded = upload(token, "a.jpg", "image/jpeg", "ug-mine");
        assertThat(uploaded.get("code").asInt()).as("已授权文件夹上传放行").isEqualTo(200);

        Integer filesBefore = jdbc.queryForObject(
            "SELECT count(*) FROM sys_file WHERE tenant_id = ? AND delete_flag = 0", Integer.class, TENANT);
        MvcResult denied = mockMvc.perform(multipart("/file/upload")
                .file(new MockMultipartFile("file", "b.txt", "text/plain", "content".getBytes()))
                .param("bizType", "ug-brandnew")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andReturn();
        assertThat(mapper.readTree(denied.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("code").asInt()).as("无投影新文件夹首传需类型级 CREATE（该用户无 → fail-closed）").isEqualTo(403);
        assertThat(countFolderProjection("ug-brandnew"))
            .as("拒绝路径不惰性登记投影").isZero();
        Integer filesAfter = jdbc.queryForObject(
            "SELECT count(*) FROM sys_file WHERE tenant_id = ? AND delete_flag = 0", Integer.class, TENANT);
        assertThat(filesAfter).as("拒绝路径不落元数据").isEqualTo(filesBefore);
    }

    @Test
    @DisplayName("upload 惰性登记：scopeAll 用户新 bizType 首传登记投影且重复上传不重复建号")
    void uploadLazilyRegistersFolderProjectionForScopeAll() throws Exception {
        long userId = insertFileAdminUser("文件夹-惰性登记");
        String token = login(userId);

        JsonNode first = upload(token, "a.txt", "text/plain", "ul-lazyreg");
        assertThat(first.get("code").asInt()).isEqualTo(200);
        assertThat(countFolderProjection("ul-lazyreg")).as("首传即成为可授权实例").isEqualTo(1L);

        JsonNode second = upload(token, "b.txt", "text/plain", "ul-lazyreg");
        assertThat(second.get("code").asInt()).isEqualTo(200);
        assertThat(countFolderProjection("ul-lazyreg")).as("重复上传不重复登记").isEqualTo(1L);
    }

    @Test
    @DisplayName("delete：本夹实例 DELETE 放行；他夹文件 403 且不软删；脏桶 fail-closed")
    void deleteGatesOnFolders() throws Exception {
        long userId = insertFolderUser("文件夹-仅本夹DELETE", Map.of("dg-mine", List.of(BIT_DELETE)));
        String token = login(userId);
        long mineFile = insertFileRow("本夹.png", "dg-mine/2026/09/06/a.png", "dg-mine");

        JsonNode deleted = postJson(token, "/file/delete", Map.of("ids", List.of(mineFile)));
        assertThat(deleted.get("code").asInt()).as("本文件夹删除放行").isEqualTo(200);
        assertThat(jdbc.queryForObject(
            "SELECT delete_flag FROM sys_file WHERE id = ?", Long.class, mineFile)).isEqualTo(mineFile);

        long otherFile = insertFileRow("他夹.pdf", "dg-other/2026/09/06/r.pdf", "dg-other");
        MvcResult denied = mockMvc.perform(post("/file/delete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("ids", List.of(otherFile)))))
            .andExpect(status().isForbidden())
            .andReturn();
        assertThat(mapper.readTree(denied.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("code").asInt()).as("他文件夹删除 403").isEqualTo(403);
        assertThat(jdbc.queryForObject(
            "SELECT delete_flag FROM sys_file WHERE id = ?", Long.class, otherFile))
            .as("拒绝路径不软删").isEqualTo(0L);

        long dirtyFile = insertFileRow("脏桶.txt", "../dg-legacy/y.txt", "../dg-legacy");
        MvcResult dirtyDenied = mockMvc.perform(post("/file/delete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("ids", List.of(dirtyFile)))))
            .andExpect(status().isForbidden())
            .andReturn();
        assertThat(mapper.readTree(dirtyDenied.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .get("code").asInt()).as("无投影历史脏桶 fail-closed 拒绝删除").isEqualTo(403);
    }
}
