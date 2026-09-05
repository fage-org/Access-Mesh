package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.AccessServiceApplication;
import cn.ac.fage.accessmesh.access.AccessServiceApplicationTest;
import cn.ac.fage.accessmesh.access.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ServiceConfigSyncResp;
import cn.ac.fage.accessmesh.access.permission.service.ServiceSyncAppService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ApiItem.operationCode 字段退役的运行时回归锁（T-PERM-053，2026-09-05）。
 * <p>
 * 该字段既不落库（resource_api_mapping 无对应列）也不参与鉴权（运行时固定
 * API:ACCESS），删除属契约修正。全局 ObjectMapper 来自 common
 * {@code CacheAutoConfiguration#cacheObjectMapper()}（裸 new，FAIL_ON_UNKNOWN_PROPERTIES
 * 未关闭，@ConditionalOnMissingBean 顶掉 Boot 宽容默认）——仍携带该字段的旧请求体
 * 在反序列化层即被拒绝。本测试经完整请求链（真实拦截器 + 上下文 mapper）锁定：
 * 新载荷（无 operationCode）反序列化成功并全量到达业务层；旧载荷（含 operationCode）
 * 400 且不触达业务层。若未来 mapper 配置漂移为宽容模式（旧载荷静默 200），本锁失败
 * ——契约反向依赖严格 mapper，漂移须经由本测试显式重审。
 * </p>
 * <p>
 * 两用例在旧实现（字段仍存在且 @NotBlank）下均失败：新载荷缺字段被 @Valid 拒 400、
 * 旧载荷携字段反序列化通过达业务层 200——锁与字段删除语义同向。
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
    "JWT_SECRET_KEY=test-jwt-secret-for-sync-operation-code-retired",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-sync-operation-code-retired",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-sync-operation-code-retired"
})
class ServiceConfigSyncOperationCodeRetiredTest {

    private static final String INTERNAL_SECRET = "test-internal-secret-for-sync-operation-code-retired";

    /** 唯一活跃调用方为管理前端服务接口配置页；此处以 SERVICE 凭证身份直达端点（业务层 mock）。 */
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceSyncAppService serviceSyncAppService;

    @Test
    @DisplayName("锁步后载荷：不含 operationCode 反序列化成功，其余字段全量到达业务层")
    void sync_withoutOperationCode_accepted() throws Exception {
        when(serviceSyncAppService.syncInterfaces(eq(1L), org.mockito.ArgumentMatchers.any(ServiceConfigSyncReq.class)))
            .thenReturn(new ServiceConfigSyncResp(1, 0, 1, 0, 0, 0));
        String body = """
            {
              "serviceCode": "my-svc",
              "syncMode": "FULL",
              "groups": [
                {
                  "groupCode": "default",
                  "groupName": "默认",
                  "apis": [
                    {
                      "name": "查询列表",
                      "httpMethod": "POST",
                      "path": "/api/demo/list",
                      "resourceCode": "my-svc:demo:list",
                      "description": "示例"
                    }
                  ]
                }
              ]
            }
            """;
        mockMvc.perform(post("/api/perm/service-config/sync")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", "my-svc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.createdMappings").value(1));

        ArgumentCaptor<ServiceConfigSyncReq> captor = ArgumentCaptor.forClass(ServiceConfigSyncReq.class);
        verify(serviceSyncAppService).syncInterfaces(eq(1L), captor.capture());
        ServiceConfigSyncReq.ApiItem api = captor.getValue().groups().get(0).apis().get(0);
        assertThat(api.name()).isEqualTo("查询列表");
        assertThat(api.httpMethod()).isEqualTo("POST");
        assertThat(api.path()).isEqualTo("/api/demo/list");
        assertThat(api.resourceCode()).isEqualTo("my-svc:demo:list");
        assertThat(api.description()).isEqualTo("示例");
    }

    @Test
    @DisplayName("旧载荷仍含 operationCode → 400（严格 mapper 未知字段拒绝），不触达业务层")
    void sync_withLegacyOperationCode_rejected400() throws Exception {
        String body = """
            {
              "serviceCode": "my-svc",
              "syncMode": "FULL",
              "groups": [
                {
                  "groupCode": "default",
                  "groupName": "默认",
                  "apis": [
                    {
                      "name": "查询列表",
                      "httpMethod": "POST",
                      "path": "/api/demo/list",
                      "operationCode": "ACCESS",
                      "resourceCode": "my-svc:demo:list",
                      "description": "示例"
                    }
                  ]
                }
              ]
            }
            """;
        mockMvc.perform(post("/api/perm/service-config/sync")
                .header("X-Tenant-Id", "1")
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .header("X-Service-Code", "my-svc")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(serviceSyncAppService);
    }
}
