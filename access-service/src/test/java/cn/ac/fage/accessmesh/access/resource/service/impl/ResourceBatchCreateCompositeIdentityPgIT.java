package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.junit.jupiter.Testcontainers;

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
 * T-PERM-076 回归锁（真实 PostgreSQL，权威 DDL 原样执行——uk_resource_entity 四列部分唯一索引生效）：
 * <ol>
 * <li><b>完整键查重</b>：查重身份=tenant+resourceType+code+归一 codeType（同唯一键）——
 *     同码跨类型（BUTTON vs DATA）、同类型跨 codeType（default vs custom）均可分别创建；
 *     存量同完整键命中逐项跳过（部分成功），批内同完整键首项胜出不撞唯一索引。</li>
 * <li><b>畸形项宽容收集</b>（2026-09-22 拍板顺手修）：code/name 空白项跳过，真实 NOT NULL
 *     约束不再连坐整批。</li>
 * <li><b>响应身份回查</b>：insertBatch 不回填自增主键（JDBC batch 限制）——成功响应 id 经
 *     完整键回查校准，可按 id 回查真实落库行（单条 create 对齐）。</li>
 * <li><b>所有权不放宽</b>：SYNC 类型（API，种子声明 SYNC+access-service）批量创建仍 20055。</li>
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
class ResourceBatchCreateCompositeIdentityPgIT {

    private static final Long TENANT = 1L;

    /** BUTTON=2 / DATA=4：种子 MANAGED 类型（API=3 为 SYNC，仅作拒绝断言） */
    private static final int BUTTON = 2;
    private static final int DATA = 4;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, ResourceBatchCreateCompositeIdentityPgIT.class);
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
    private RoleResourcePermissionDomainService rolePermDomainService;
    @Autowired
    private cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService serviceConfigDomainService;
    @Autowired
    private cn.ac.fage.accessmesh.access.type.mapper.TypeDefinitionMapper typeDefinitionMapper;

    private final LocalDateTime now = LocalDateTime.now();

    private PermQueryEngine permitAllEngine() {
        PermQueryEngine engine = mock(PermQueryEngine.class);
        lenient().when(engine.hasPermissionByCode(anyLong(), any(), any(), any(), any())).thenReturn(true);
        lenient().when(engine.hasPermissionByEntityId(anyLong(), any(), any(), any(), any())).thenReturn(true);
        return engine;
    }

    private ResourceManageAppServiceImpl newResourceManageAppService(PermQueryEngine engine) {
        return new ResourceManageAppServiceImpl(resourceEntityMapper, resourceApiMappingMapper,
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper.class),
            resourceEntityDomainService, typeResolutionService, domainClassifyService,
            engine, rolePermDomainService,
            mock(cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService.class),
            new cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard(
                typeDefinitionMapper, serviceConfigDomainService, resourceEntityDomainService,
                new com.fasterxml.jackson.databind.ObjectMapper()),
            mock(TreeWriteLockSupport.class));
    }

    @BeforeEach
    void bindContext() {
        PermissionChangeContext.bindIfAbsent();
    }

    @AfterEach
    void clearContext() {
        PermissionChangeContext.clear();
    }

    private ResourceEntity insertResource(int resourceType, String code, String codeType) {
        ResourceEntity entity = new ResourceEntity();
        entity.setTenantId(TENANT);
        entity.setResourceType(resourceType);
        entity.setCode(code);
        entity.setCodeType(codeType);
        entity.setName(code + " 资源");
        entity.setStatus(1);
        entity.setExtra("{}");
        entity.setMaintainSource("MANUAL");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleteFlag(0L);
        resourceEntityMapper.insert(entity);
        return entity;
    }

    private static ResourceCreateReq batchReq(String typeCode, String code, String codeType, String name) {
        return new ResourceCreateReq(null, null, null, null, null, typeCode, code, codeType, name, null, null, null);
    }

    @Test
    @DisplayName("同码跨类型可创建 + 响应 id 回查真实落库行（真实 uk_resource_entity 放行；旧实现 tenant+code 查重误拒）")
    void crossTypeSameCodeCreatableWithRealIdEcho() {
        // 存量：BUTTON/R076A/default——同码不同类型，不构成 DATA 新项重复
        insertResource(BUTTON, "R076A", "default");

        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());
        List<cn.ac.fage.accessmesh.access.resource.dto.resp.ResourceResp> resp;
        try (MockedStatic<cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext> operatorContext =
                 mockStatic(cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext.class)) {
            operatorContext.when(cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext::getOperatorId)
                .thenReturn(100L);
            resp = service.batchCreateResources(TENANT, List.of(
                batchReq("DATA", "R076A", null, "同码跨类型")), 100L);
        }

        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).id()).isNotNull();
        // 响应主键可回查真实落库行（insertBatch 不回填——回查校准面）
        ResourceEntity saved = resourceEntityMapper.selectValidById(TENANT, resp.get(0).id());
        assertThat(saved).isNotNull();
        assertThat(saved.getCode()).isEqualTo("R076A");
        assertThat(saved.getResourceType()).isEqualTo(DATA);
        assertThat(saved.getCodeType()).isEqualTo("default");
    }

    @Test
    @DisplayName("同类型跨 codeType 可创建（default 存量不拦 custom 新项）")
    void sameTypeCrossCodeTypeCreatable() {
        insertResource(DATA, "R076B", "default");

        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());
        var resp = service.batchCreateResources(TENANT, List.of(
            batchReq("DATA", "R076B", "custom", "同类型跨codeType")), 100L);

        assertThat(resp).hasSize(1);
        ResourceEntity saved = resourceEntityMapper.selectValidById(TENANT, resp.get(0).id());
        assertThat(saved).isNotNull();
        assertThat(saved.getCodeType()).isEqualTo("custom");
    }

    @Test
    @DisplayName("存量同完整键命中逐项跳过、部分成功（不整批 SQL 失败）")
    void existingFullKeySkippedPartiallySucceeds() {
        insertResource(BUTTON, "R076C", "default");

        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());
        var resp = service.batchCreateResources(TENANT, List.of(
            batchReq("BUTTON", "R076C", null, "存量重复项"),
            batchReq("DATA", "R076D", null, "正常项")), 100L);

        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).code()).isEqualTo("R076D");
        // 存量行未被重复创建（真实唯一键下唯一一行）
        assertThat(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, BUTTON, "R076C", "default"))
            .extracting(ResourceEntity::getName)
            .isEqualTo("R076C 资源");
    }

    @Test
    @DisplayName("批内同完整键首项胜出、不撞真实唯一索引（旧实现两项齐入整批 SQL 失败）")
    void intraBatchDuplicateFullKeyFirstWinsOnRealUniqueIndex() {
        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());
        var resp = service.batchCreateResources(TENANT, List.of(
            batchReq("DATA", "R076E", null, "首项"),
            batchReq("DATA", "R076E", null, "后到同键项"),
            batchReq("DATA", "R076F", null, "正常项")), 100L);

        assertThat(resp).hasSize(2);
        ResourceEntity winner = resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, DATA, "R076E", "default");
        assertThat(winner).isNotNull();
        assertThat(winner.getName()).isEqualTo("首项");
        assertThat(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT, DATA, "R076F", "default")).isNotNull();
    }

    @Test
    @DisplayName("畸形项（code/name 空白）宽容收集跳过——真实 NOT NULL 约束不再连坐整批（2026-09-22 拍板顺手修）")
    void malformedItemsCollectedNotBatchFailing() {
        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());
        var resp = service.batchCreateResources(TENANT, java.util.Arrays.asList(
            batchReq("DATA", null, null, "缺code"),
            batchReq("DATA", "R076G", null, null),
            batchReq("DATA", "R076H", null, "正常项")), 100L);

        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).code()).isEqualTo("R076H");
    }

    @Test
    @DisplayName("所有权门禁不放宽：SYNC 类型（API）批量创建仍 20055（真实类型声明生效）")
    void syncTypeStillRejectedByRealOwnershipGuard() {
        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());
        assertThatThrownBy(() -> service.batchCreateResources(TENANT, List.of(
                batchReq("API", "R076I", null, "SYNC类型")), 100L))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(20055);
    }

    @Test
    @DisplayName("全批类型码 null/空白：真实守卫空集返回 Map.of() 下逐项宽容跳过，不再整批 NPE（双轨评审 P3-1）")
    void allNullTypeCodeBatchCollectedOnRealGuard() {
        ResourceManageAppServiceImpl service = newResourceManageAppService(permitAllEngine());
        // 真实 ResourceTypeOwnershipGuard 空入参返回 Map.of()——旧实现 Map.of().get(null) NPE 整批 500
        var resp = service.batchCreateResources(TENANT, java.util.Arrays.asList(
            batchReq(null, "R076L", null, "类型码null"),
            batchReq("  ", "R076L2", null, "类型码空白")), 100L);

        assertThat(resp).isEmpty();
    }
}
