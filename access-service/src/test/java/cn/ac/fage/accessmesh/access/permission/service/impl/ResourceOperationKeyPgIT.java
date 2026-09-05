package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeysReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.OperationUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceMoveReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * T-PERM-028 回归锁（真实 PostgreSQL，权威 DDL 原样执行）：
 * <ol>
 * <li><b>资源业务键链路</b>：detail/update/move/remove 以 (resourceTypeCode, code, codeType)
 *     定位走通真实 SQL（selectByTypeCodeAndCodeType / selectByTypeAndCodesAndCodeTypes）；
 *     codeType 缺省归一 default；extraClear 清空；move 跨类型/子孙目标 20053 拒绝；
 *     remove 按键级联软删子孙（真实递归 CTE）。</li>
 * <li><b>操作权限业务键链路</b>：detail/update/remove 以 (resourceTypeCode, code) 定位；
 *     全局操作概念已退役（2026-08-30 设计定案），DDL CHECK 强制 resource_type 非空，
 *     全局行在数据层被拒（本测试真库断言）。</li>
 * <li><b>resource_type 创建联动预置</b>：createType 同事务插入 CRUD 四操作位，
 *     位值对齐 DDL 预置组模板，真实 uk_operation_permission_typed/_typed_bit 约束生效。</li>
 * </ol>
 * 门禁用 mock 引擎放行（门禁语义由单测锁定）；Docker 不可用时自动跳过。
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
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class ResourceOperationKeyPgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("resource_operation_key_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐（见 UserRoleWriteProjectionPgIT 同款说明） */
    private static final String REDIS_TEST_PASSWORD = "accessmesh-test";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD)
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
    }

    @BeforeAll
    static void setupSchema() throws Exception {
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private ResourceEntityMapper resourceEntityMapper;
    @Autowired
    private ResourceApiMappingMapper resourceApiMappingMapper;
    @Autowired
    private ResourceEntityDomainService resourceEntityDomainService;
    @Autowired
    private TypeResolutionService typeResolutionService;
    @Autowired
    private DomainClassifyService domainClassifyService;
    @Autowired
    private RoleResourcePermissionMapper rolePermMapper;
    @Autowired
    private OperationPermissionMapper operationPermissionMapper;
    @Autowired
    private TypeDefinitionMapper typeDefinitionMapper;
    @Autowired
    private cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper serviceConfigMapper;

    private cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard ownershipGuard() {
        return new cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard(
                typeDefinitionMapper, serviceConfigMapper, resourceEntityDomainService, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private final LocalDateTime now = LocalDateTime.now();

    private PermQueryEngine permitAllEngine() {
        PermQueryEngine engine = mock(PermQueryEngine.class);
        lenient().when(engine.hasPermissionByCode(anyLong(), any(), any(), any(), any())).thenReturn(true);
        lenient().when(engine.hasPermissionByEntityId(anyLong(), any(), any(), any(), any())).thenReturn(true);
        lenient().when(engine.getDeniedResourceCodes(anyLong(), any(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        lenient().when(engine.getDeniedEntityIds(anyLong(), any(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        return engine;
    }

    private ResourceManageAppServiceImpl newResourceManageAppService(PermQueryEngine engine) {
        return new ResourceManageAppServiceImpl(resourceEntityMapper, resourceApiMappingMapper,
            resourceEntityDomainService, typeResolutionService, domainClassifyService,
            engine, rolePermMapper, ownershipGuard(),
            mock(TreeWriteLockSupport.class));
    }

    private OperationAppServiceImpl newOperationAppService(PermQueryEngine engine) {
        return new OperationAppServiceImpl(operationPermissionMapper, typeResolutionService, engine);
    }

    @BeforeEach
    void bindContext() {
        PermissionChangeContext.bindIfAbsent();
    }

    @AfterEach
    void clearContext() {
        PermissionChangeContext.clear();
    }

    private ResourceEntity insertResource(int resourceType, String code, String codeType, Long parentId) {
        ResourceEntity entity = new ResourceEntity();
        entity.setTenantId(TENANT);
        entity.setResourceType(resourceType);
        entity.setCode(code);
        entity.setCodeType(codeType);
        entity.setName(code + " 资源");
        entity.setStatus(1);
        entity.setSortOrder(0);
        entity.setExtra("{}");
        entity.setParentId(parentId);
        entity.setMaintainSource("MANUAL");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleteFlag(0L);
        resourceEntityMapper.insert(entity);
        return entity;
    }

    private OperationPermission insertOperation(Integer resourceType, String code, long bit, long mask) {
        OperationPermission op = new OperationPermission();
        op.setTenantId(TENANT);
        op.setResourceType(resourceType);
        op.setCode(code);
        op.setName(code);
        op.setBinaryBit(bit);
        op.setInheritMask(mask);
        op.setCreatedAt(now);
        op.setUpdatedAt(now);
        op.setDeleteFlag(0L);
        operationPermissionMapper.insert(op);
        return op;
    }

    @Test
    @DisplayName("createResource 落库 maintain_source=MANUAL（T-FE-017 冒烟缺陷回归锁——flex insert 显式带列，DDL DEFAULT 不生效，缺设置则 NOT NULL 500）")
    void createResourceShouldPersistMaintainSourceManual() {
        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            // BUTTON 未声明 SYNC（事实链路四类型之外），管理面可建（原保留清单已收编进类型门禁）
            var resp = service.createResource(TENANT, new ResourceCreateReq(
                null, null, null, null, null, "BUTTON", "PGIT17_CREATE", null,
                "冒烟资源", null, null, null, "{}"), 100L);
            ResourceEntity saved = resourceEntityMapper.selectValidById(TENANT, resp.id());
            assertThat(saved).isNotNull();
            assertThat(saved.getMaintainSource()).isEqualTo("MANUAL");
        }
    }

    @Test
    @DisplayName("资源业务键全链路：detail（codeType 缺省归一）→ update（extraClear）→ move（防环）→ remove（级联子孙）")
    void shouldWalkResourceBusinessKeyChainOnRealPostgres() {
        // T-PERM-052 内部来源统一后 MENU 属事实链路四类型（种子声明 SYNC+access-service，
        // 管理面资源 CRUD 20055 只读）——业务键链路夹具改用 MANAGED 的 DATA 类型
        ResourceEntity parent = insertResource(4, "PGIT28_ROOT", "default", null);
        ResourceEntity child = insertResource(4, "PGIT28_CHILD", "default", parent.getId());
        ResourceEntity button = insertResource(2, "PGIT28_BTN", "default", null);

        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            // detail：codeType 缺省归一 default 命中真实行
            assertThat(service.getResource(TENANT, new ResourceKeyReq("DATA", "PGIT28_CHILD", null)).id())
                .isEqualTo(child.getId());
            // 查不到 → 20004
            assertThatThrownBy(() -> service.getResource(TENANT, new ResourceKeyReq("DATA", "PGIT28_NONE", null)))
                .isInstanceOf(BizException.class);

            // update：业务键定位 + extraClear 清空
            service.updateResource(TENANT, new ResourceUpdateReq("DATA", "PGIT28_CHILD", null,
                "子资源改名", null, null, null, null, Boolean.TRUE), 100L);
            ResourceEntity updated = resourceEntityMapper.selectValidById(TENANT, child.getId());
            assertThat(updated.getName()).isEqualTo("子资源改名");
            assertThat(updated.getExtra()).isNull();

            // move：跨类型（BUTTON 父）→ 20053
            assertThatThrownBy(() -> service.moveResource(TENANT, new ResourceMoveReq(
                new ResourceKeyReq("DATA", "PGIT28_CHILD", null),
                new ResourceKeyReq("BUTTON", "PGIT28_BTN", null)), 100L))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(20053);

            // move：目标父为子孙（root 移到 child 下）→ 20053（真实递归 CTE 判定）
            assertThatThrownBy(() -> service.moveResource(TENANT, new ResourceMoveReq(
                new ResourceKeyReq("DATA", "PGIT28_ROOT", null),
                new ResourceKeyReq("DATA", "PGIT28_CHILD", null)), 100L))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(20053);

            // move：合法同类型移动（child 先移到顶层、再移回 root 下——双向覆盖）
            service.moveResource(TENANT, new ResourceMoveReq(
                new ResourceKeyReq("DATA", "PGIT28_CHILD", null), null), 100L);
            assertThat(resourceEntityMapper.selectValidById(TENANT, child.getId()).getParentId()).isNull();
            service.moveResource(TENANT, new ResourceMoveReq(
                new ResourceKeyReq("DATA", "PGIT28_CHILD", null),
                new ResourceKeyReq("DATA", "PGIT28_ROOT", null)), 100L);
            assertThat(resourceEntityMapper.selectValidById(TENANT, child.getId()).getParentId())
                .isEqualTo(parent.getId());
        }

        // remove：按业务键删除 root，级联软删子孙（真实递归 CTE）
        service.deleteResources(TENANT, List.of(new ResourceKeyReq("DATA", "PGIT28_ROOT", null)), 100L);
        assertThat(resourceEntityMapper.selectValidById(TENANT, parent.getId())).isNull();
        assertThat(resourceEntityMapper.selectValidById(TENANT, child.getId())).isNull();
        // 其他类型资源不受影响
        assertThat(resourceEntityMapper.selectValidById(TENANT, button.getId())).isNotNull();
    }

    @Test
    @DisplayName("操作权限业务键链路：双类型 detail → update → remove（真实 uk 约束）+ 全局行被 CHECK 拒绝")
    void shouldWalkOperationBusinessKeyChainOnRealPostgres() throws Exception {
        OperationPermission typed = insertOperation(5, "PGIT28_OP_T", 1024L, 0L);
        OperationPermission userScoped = insertOperation(6, "PGIT28_OP_U", 2048L, 0L);

        OperationAppServiceImpl service = newOperationAppService(permitAllEngine());

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            // detail：两类型各自按业务键命中
            assertThat(service.getOperation(TENANT, new OperationKeyReq("ROLE", "PGIT28_OP_T")).id())
                .isEqualTo(typed.getId());
            assertThat(service.getOperation(TENANT, new OperationKeyReq("USER", "PGIT28_OP_U")).id())
                .isEqualTo(userScoped.getId());

            // update：业务键定位更新位值
            service.updateOperation(TENANT, new OperationUpdateReq("ROLE", "PGIT28_OP_T", "改名", 4096L, 2L), 100L);
            OperationPermission updated = operationPermissionMapper.selectValidById(typed.getId(), TENANT);
            assertThat(updated.getName()).isEqualTo("改名");
            assertThat(updated.getBinaryBit()).isEqualTo(4096L);
            assertThat(updated.getInheritMask()).isEqualTo(2L);
        }

        // remove：按键批量（双类型混排）
        service.deleteOperations(TENANT, List.of(
            new OperationKeyReq("ROLE", "PGIT28_OP_T"),
            new OperationKeyReq("USER", "PGIT28_OP_U")), 100L);
        assertThat(operationPermissionMapper.selectValidById(typed.getId(), TENANT)).isNull();
        assertThat(operationPermissionMapper.selectValidById(userScoped.getId(), TENANT)).isNull();

        // 全局操作概念退役：DDL CHECK 强制 resource_type 非空，全局行在数据层被拒
        // （授权行只存 resource_type + granted_bits，全局位与专属位同值时授权身份不可区分）
        try (var conn = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var ps = conn.prepareStatement(
                "INSERT INTO operation_permission (tenant_id, resource_type, code, name, "
                    + "binary_bit, inherit_mask, delete_flag) VALUES (?, NULL, 'PGIT28_OP_GLOBAL', 'x', 4096, 0, 0)")) {
            ps.setLong(1, TENANT);
            assertThatThrownBy(ps::executeUpdate)
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("ck_operation_permission_resource_type_required");
        }
    }

    @Test
    @DisplayName("resource_type 创建联动预置 CRUD 四操作位（真实 uk 约束 + DDL 模板位值）")
    void shouldPresetCrudOperationsWhenCreatingResourceTypeOnRealPostgres() {
        TypeDefinitionAppServiceImpl typeService = new TypeDefinitionAppServiceImpl(
            typeDefinitionMapper, operationPermissionMapper, permitAllEngine(), ownershipGuard(),
            resourceEntityDomainService, mock(TreeWriteLockSupport.class),
            mock(cn.ac.fage.accessmesh.common.cache.CacheService.class));

        var resp = typeService.createType(TENANT,
            new TypeCreateReq("resource_type", "PGIT28_TYPE", "联调测试类型", null, null, null), 100L);

        List<OperationPermission> preset = operationPermissionMapper
            .selectByTenantAndResourceType(TENANT, resp.typeValue());
        assertThat(preset).hasSize(4);
        assertThat(preset.stream().map(OperationPermission::getCode))
            .containsExactlyInAnyOrder("CREATE", "VIEW", "UPDATE", "DELETE");
        for (OperationPermission op : preset) {
            switch (op.getCode()) {
                case "CREATE" -> { assertThat(op.getBinaryBit()).isEqualTo(1L); assertThat(op.getInheritMask()).isEqualTo(0L); }
                case "VIEW" -> { assertThat(op.getBinaryBit()).isEqualTo(2L); assertThat(op.getInheritMask()).isEqualTo(0L); }
                case "UPDATE" -> { assertThat(op.getBinaryBit()).isEqualTo(4L); assertThat(op.getInheritMask()).isEqualTo(2L); }
                case "DELETE" -> { assertThat(op.getBinaryBit()).isEqualTo(8L); assertThat(op.getInheritMask()).isEqualTo(2L); }
                default -> throw new AssertionError("意外操作码: " + op.getCode());
            }
        }
    }
}
