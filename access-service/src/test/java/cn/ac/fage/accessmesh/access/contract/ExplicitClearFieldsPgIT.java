package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.bootstrap.BootstrapGraphDefinition;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.resource.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.resource.service.ServiceConfigAppService;
import cn.ac.fage.accessmesh.access.type.service.TypeDefinitionAppService;
import cn.ac.fage.accessmesh.access.user.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.user.service.UserWriteAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 显式清空协议真实库回归锁（T-API-004，U006 四项拍板；JSONB/NULL 列/唯一索引真实行为）。
 * <p>
 * 锁面：清空必须真实落库为 NULL（update(entity) 忽略 null 列——单测 mock mapper 掩盖该
 * 失效形态，必须真库断言，T-PERM-028/T-FE-016 role 域先例）；phone 多行 NULL 共存不撞
 * uk_user_phone（部分唯一索引 WHERE phone IS NOT NULL）；type extraClear 服务层拒绝；
 * service 创建分支拒清空标志。旧实现（无清空分支，xxxClear 被静默忽略）下各清空断言必红
 * （库值保持原值非 NULL）。
 * </p>
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "access.bootstrap.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExplicitClearFieldsPgIT {

    private static final Long TENANT = BootstrapGraphDefinition.TENANT_ID;
    private static final String BOOTSTRAP_PASSWORD = "ClearFields-IT-2026!";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, ExplicitClearFieldsPgIT.class);
    }

    @Autowired
    private AccessBootstrapInitializer initializer;
    @Autowired
    private UserWriteAppService userWriteAppService;
    @Autowired
    private UserManageAppService userManageAppService;
    @Autowired
    private TypeDefinitionAppService typeDefinitionAppService;
    @Autowired
    private ServiceConfigAppService serviceConfigAppService;
    @Autowired
    private ResourceManageAppService resourceManageAppService;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void seedAndBindAdmin() {
        initializer.initialize(BOOTSTRAP_PASSWORD);
        Long adminId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        assertThat(adminId).isNotNull();
        bindOperator(adminId);
    }

    @AfterEach
    void cleanupContext() {
        // 上下文保留至类结束（@TestInstance PER_CLASS 共享绑定），仅清租户残留防线
        TenantContextHolder.setTenantId(TENANT);
    }

    private void bindOperator(Long operatorId) {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, operatorId));
    }

    @Test
    @DisplayName("admin 轨 phone/email 清空真实落 NULL，两用户同清不撞 uk_user_phone（部分唯一索引）")
    void userPhoneEmailClearPersistsNull() {
        var u1 = userWriteAppService.createUser(new cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq(
            "it-clear-u1", "清空用户一", "13800000001", "u1@it.local", null, null, null));
        var u2 = userWriteAppService.createUser(new cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq(
            "it-clear-u2", "清空用户二", "13800000002", "u2@it.local", null, null, null));

        userWriteAppService.updateUser(new cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateReq(
            u1.id(), null, null, null, null, true, true));
        userWriteAppService.updateUser(new cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateReq(
            u2.id(), null, null, null, null, true, null));

        assertThat(jdbc.queryForObject(
            "SELECT phone FROM sys_user WHERE tenant_id = ? AND id = ?", String.class, TENANT, u1.id()))
            .as("phoneClear=true 后 phone 必须真实落 NULL（旧实现忽略标志、值保持原值）").isNull();
        assertThat(jdbc.queryForObject(
            "SELECT email FROM sys_user WHERE tenant_id = ? AND id = ?", String.class, TENANT, u1.id())).isNull();
        assertThat(jdbc.queryForObject(
            "SELECT phone FROM sys_user WHERE tenant_id = ? AND id = ?", String.class, TENANT, u2.id()))
            .as("第二个用户同清 phone=NULL 不撞 uk_user_phone（部分唯一索引排除 NULL）").isNull();

        // 投影不受清空影响（abstract_user 行仍在且未软删）
        assertThat(jdbc.queryForObject(
            "SELECT delete_flag FROM abstract_user WHERE tenant_id = ? AND id = ?", Long.class, TENANT, u1.id()))
            .isZero();
    }

    @Test
    @DisplayName("perm 轨 abstract-user extra 清空真实落 NULL，投影链不受影响")
    void permTrackUserExtraClearPersistsNull() {
        var created = userManageAppService.createUser(TENANT,
            new cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserCreateReq(
                "USER", "it-clear-ext-1", "外部清空用户", true, "{\"k\":1}"));
        assertThat(jdbc.queryForObject(
            "SELECT extra FROM abstract_user WHERE tenant_id = ? AND id = ?", String.class, TENANT, created.id()))
            .isNotNull();

        userManageAppService.updateUser(TENANT,
            new cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserUpdateReq(
                created.id(), null, null, null, true));

        assertThat(jdbc.queryForObject(
            "SELECT extra FROM abstract_user WHERE tenant_id = ? AND id = ?", String.class, TENANT, created.id()))
            .as("extraClear=true 后 extra 必须真实落 NULL（update(entity) 忽略 null 列的失效面）").isNull();
        // USER 资源投影仍存在（清空 extra 不触达投影）
        assertThat(jdbc.queryForObject(
            "SELECT delete_flag FROM resource_entity WHERE tenant_id = ? AND resource_type = 6 AND code = ?",
            Long.class, TENANT, String.valueOf(created.id()))).isZero();
    }

    @Test
    @DisplayName("type description 清空落 NULL；extraClear 任何非 null 值服务层拒绝（U006 拍板）")
    void typeDescriptionClearPersistsAndExtraClearRejected() {
        String typeCode = "it-clear-type";
        jdbc.update(
            "INSERT INTO type_definition(tenant_id, type_key, type_code, type_value, name, description, is_system, extra) "
                + "VALUES (?, 'biz_flag', ?, 9, '清空协议类型', '旧描述', false, '{}')", TENANT, typeCode);
        Long typeId = jdbc.queryForObject(
            "SELECT id FROM type_definition WHERE tenant_id = ? AND type_code = ?", Long.class, TENANT, typeCode);

        typeDefinitionAppService.updateType(TENANT,
            new cn.ac.fage.accessmesh.access.type.dto.req.TypeUpdateReq(typeId, null, null, null, null, true, null), null);
        assertThat(jdbc.queryForObject(
            "SELECT description FROM type_definition WHERE id = ?", String.class, typeId))
            .as("descriptionClear=true 后 description 必须真实落 NULL").isNull();

        // extra 携带新值照常可写（非 resource_type 类型不受所有权声明校验拦截的空 JSON）
        typeDefinitionAppService.updateType(TENANT,
            new cn.ac.fage.accessmesh.access.type.dto.req.TypeUpdateReq(typeId, null, "新描述", null, "{}", null, null), null);
        assertThat(jdbc.queryForObject(
            "SELECT description FROM type_definition WHERE id = ?", String.class, typeId)).isEqualTo("新描述");

        assertThatThrownBy(() -> typeDefinitionAppService.updateType(TENANT,
            new cn.ac.fage.accessmesh.access.type.dto.req.TypeUpdateReq(typeId, null, null, null, null, null, true), null))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("类型 extra 不支持清空");
        // 拒绝后 extra 未被破坏
        assertThat(jdbc.queryForObject(
            "SELECT extra FROM type_definition WHERE id = ?", String.class, typeId)).isEqualTo("{}");
    }

    @Test
    @DisplayName("service basePath/description/extra 清空真实落 NULL；创建分支携带清空标志拒绝")
    void serviceConfigClearPersistsAndCreateRejected() {
        serviceConfigAppService.saveServiceConfig(TENANT,
            new cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq(
                "it-clear-svc", "清空协议服务", "/it-clear", "旧描述", null, "{\"syncTypes\":{}}", null, null, null),
            null);

        serviceConfigAppService.saveServiceConfig(TENANT,
            new cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq(
                "it-clear-svc", "清空协议服务", null, null, null, null, true, true, true),
            null);

        assertThat(jdbc.queryForObject(
            "SELECT base_path FROM service_config WHERE tenant_id = ? AND service_code = ?",
            String.class, TENANT, "it-clear-svc"))
            .as("basePathClear=true 后 base_path 必须真实落 NULL").isNull();
        assertThat(jdbc.queryForObject(
            "SELECT description FROM service_config WHERE tenant_id = ? AND service_code = ?",
            String.class, TENANT, "it-clear-svc")).isNull();
        assertThat(jdbc.queryForObject(
            "SELECT extra FROM service_config WHERE tenant_id = ? AND service_code = ?",
            String.class, TENANT, "it-clear-svc"))
            .as("extraClear=true=撤销 syncTypes 白名单（fail-closed），落 NULL").isNull();

        assertThatThrownBy(() -> serviceConfigAppService.saveServiceConfig(TENANT,
            new cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq(
                "it-clear-svc-new", "新服务", null, null, null, null, true, null, null),
            null))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("创建服务配置不接受");
        // 创建分支拒绝后无行落库
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM service_config WHERE tenant_id = ? AND service_code = ?",
            Long.class, TENANT, "it-clear-svc-new")).isZero();
    }

    @Test
    @DisplayName("接口映射 extra 清空真实落 NULL（JSONB 列显式 NULL 写入）")
    void apiMappingExtraClearPersistsNull() {
        jdbc.update("INSERT INTO service_config(tenant_id, service_code, name) VALUES (?, 'it-clear-map-svc', '映射服务')", TENANT);
        jdbc.update(
            "INSERT INTO resource_entity(tenant_id, resource_type, code, code_type, name, path, status, extra) "
                + "VALUES (?, 3, 'it-clear-api-1', 'default', '清空映射资源', '/it/clear', 1, '{}')", TENANT);
        Long resourceId = jdbc.queryForObject(
            "SELECT id FROM resource_entity WHERE tenant_id = ? AND code = 'it-clear-api-1'", Long.class, TENANT);
        jdbc.update(
            "INSERT INTO resource_api_mapping(tenant_id, resource_entity_id, service_code, http_method, path_pattern, enabled, extra) "
                + "VALUES (?, ?, 'it-clear-map-svc', 'POST', '/it/clear/**', true, '{\"k\":1}')", TENANT, resourceId);
        Long mappingId = jdbc.queryForObject(
            "SELECT id FROM resource_api_mapping WHERE tenant_id = ? AND resource_entity_id = ?", Long.class, TENANT, resourceId);

        resourceManageAppService.updateApiMapping(TENANT,
            new cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingUpdateReq(
                resourceId, mappingId, null, null, null, null, null, true));

        assertThat(jdbc.queryForObject(
            "SELECT extra FROM resource_api_mapping WHERE id = ?", String.class, mappingId))
            .as("extraClear=true 后 extra 必须真实落 NULL").isNull();
    }
}
