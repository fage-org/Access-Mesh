package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.AccessServiceApplication;
import cn.ac.fage.accessmesh.access.AccessServiceApplicationTest;
import cn.ac.fage.accessmesh.access.resource.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.resource.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * resource 面 sortOrder 字段退役的运行时回归锁（T-ACCESS-036，2026-09-13）。
 * <p>
 * {@code resource_entity.sort_order} 全仓零读取方（无任何按该列排序的消费；列表分页
 * ORDER BY id，资源树不按该列排序），停写属死数据写入消除。退役面覆盖管理面
 * create/update（SDK 单源 ResourceCreateReq/ResourceUpdateReq）与资源同步通道
 * （ResourceEntitySyncReq / full-sync 的 ResourceEntitySyncItem）四个请求 DTO。
 * 全局 ObjectMapper 来自 common {@code CacheAutoConfiguration#cacheObjectMapper()}
 * （裸 new，FAIL_ON_UNKNOWN_PROPERTIES 未关闭，@ConditionalOnMissingBean 顶掉 Boot
 * 宽容默认）——仍携带该字段的旧请求体在反序列化层即被拒绝（HttpMessageNotReadableException
 * → 90001 信封，与 T-PERM-053 operationCode 退役同一机制）。本测试经完整请求链
 * （真实拦截器 + 上下文 mapper）锁定：新载荷（无 sortOrder）反序列化成功并全量到达
 * 业务层；四个 DTO 面的旧载荷（含 sortOrder）均 400 且不触达业务层。若未来 mapper
 * 配置漂移为宽容模式（旧载荷静默 200），本锁失败——契约反向依赖严格 mapper，漂移
 * 须经由本测试显式重审。
 * </p>
 */
@SpringBootTest(
    classes = {AccessServiceApplication.class, AccessServiceApplicationTest.TestDataSourceConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-resource-sort-order-retired",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-resource-sort-order-retired",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-resource-sort-order-retired"
})
class ResourceSortOrderRetiredTest {

    private static final String INTERNAL_SECRET = "test-internal-secret-for-resource-sort-order-retired";
    private static final String SERVICE_CODE = "my-svc";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResourceManageAppService resourceManageAppService;

    @MockBean
    private ResourceEntitySyncAppService resourceEntitySyncAppService;

    @Test
    @DisplayName("锁步后载荷：update 不含 sortOrder 反序列化成功，其余字段全量到达业务层")
    void update_withoutSortOrder_accepted() throws Exception {
        when(resourceManageAppService.updateResource(eq(1L), org.mockito.ArgumentMatchers.any(), isNull()))
            .thenReturn(new ResourceResp(101L, 1L, null, "DATA", "数据", "r-1", "default",
                "改名", null, 1, "{}", null, null));
        String body = """
            {
              "resourceTypeCode": "DATA",
              "code": "r-1",
              "codeType": "default",
              "name": "改名",
              "status": 1,
              "extra": "{}"
            }
            """;
        mockMvc.perform(post("/api/access/resource-entity/update")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", SERVICE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("改名"));

        ArgumentCaptor<cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq> captor =
            ArgumentCaptor.forClass(cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq.class);
        verify(resourceManageAppService).updateResource(eq(1L), captor.capture(), isNull());
        assertThat(captor.getValue().resourceTypeCode()).isEqualTo("DATA");
        assertThat(captor.getValue().code()).isEqualTo("r-1");
        assertThat(captor.getValue().name()).isEqualTo("改名");
        assertThat(captor.getValue().status()).isEqualTo(1);
    }

    @Test
    @DisplayName("旧载荷 create 仍含 sortOrder → 400（严格 mapper 未知字段拒绝），不触达业务层")
    void create_withLegacySortOrder_rejected400() throws Exception {
        String body = """
            {
              "resourceTypeCode": "DATA",
              "code": "r-1",
              "name": "资源A",
              "sortOrder": 10
            }
            """;
        mockMvc.perform(post("/api/access/resource-entity/create")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", SERVICE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            // 信封码断言锁定 400 + 90001 形态（HttpMessageNotReadable 通道；负向载荷
            // @NotBlank 字段全提供不触发 @Valid，400 只能来自反序列化拒绝——通道组合
            // 由正向用例经同一请求链到达业务层补证）
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_FAILED.code()));

        verifyNoInteractions(resourceManageAppService);
    }

    @Test
    @DisplayName("旧载荷 update 仍含 sortOrder → 400，不触达业务层")
    void update_withLegacySortOrder_rejected400() throws Exception {
        String body = """
            {
              "resourceTypeCode": "DATA",
              "code": "r-1",
              "codeType": "default",
              "name": "改名",
              "status": 1,
              "sortOrder": 10
            }
            """;
        mockMvc.perform(post("/api/access/resource-entity/update")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", SERVICE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_FAILED.code()));

        verifyNoInteractions(resourceManageAppService);
    }

    @Test
    @DisplayName("旧载荷 sync 仍含 sortOrder → 400，不触达业务层")
    void sync_withLegacySortOrder_rejected400() throws Exception {
        String body = """
            {
              "operation": "UPSERT",
              "resourceTypeCode": "MY_TYPE",
              "resourceCode": "2001",
              "codeType": "default",
              "name": "研发部",
              "status": 1,
              "sortOrder": 10,
              "sourceService": "my-svc",
              "syncVersion": { "occurredAt": "2026-06-12T10:00:00.123", "sequenceNo": 1024 }
            }
            """;
        mockMvc.perform(post("/api/access/resource-entity/sync")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", SERVICE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_FAILED.code()));

        verifyNoInteractions(resourceEntitySyncAppService);
    }

    @Test
    @DisplayName("旧载荷 full-sync item 仍含 sortOrder → 400，不触达业务层")
    void fullSync_withLegacySortOrder_rejected400() throws Exception {
        String body = """
            {
              "scope": { "sourceService": "my-svc", "resourceTypeCode": "MY_TYPE" },
              "items": [
                {
                  "resourceCode": "2001",
                  "codeType": "default",
                  "name": "研发部",
                  "status": 1,
                  "sortOrder": 10,
                  "syncVersion": { "occurredAt": "2026-06-12T10:00:00.123", "sequenceNo": 1024 }
                }
              ]
            }
            """;
        mockMvc.perform(post("/api/access/resource-entity/full-sync")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", SERVICE_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_FAILED.code()));

        verifyNoInteractions(resourceEntitySyncAppService);
    }
}
