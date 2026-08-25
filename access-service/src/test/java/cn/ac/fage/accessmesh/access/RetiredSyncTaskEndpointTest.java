package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.AccessServiceApplication;
import cn.ac.fage.accessmesh.access.AccessServiceApplicationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退役接口运行时负向验收（T-ACCESS-011 验收 5）。
 * <p>
 * 映射级排除由 HttpApiPathSnapshotTest（注解扫描 + 快照）覆盖；本测试从运行时
 * 补足另一半证据：全量 Spring Context 启动后，注册的 RequestMappingHandlerMapping
 * 不含任何 /sync-task、/audit-log 或 extra-roles 映射（含非注解注册通道），且经完整
 * 请求链实际请求被 pre-handler fail-closed 拒绝（admin 路径族 401、/api/perm/** 路径族
 * 403、无拦截覆盖路径 404），不命中任何业务处理器（详见 retiredPathRejected 说明）。
 * 外部口径：Gateway /admin/sync-task/*（StripPrefix=1）→ access-service /sync-task/*。
 * </p>
 * <ul>
 *   <li>/sync-task/*：T-ACCESS-005 删除内部同步子系统（sys_sync_task + Controller）。</li>
 *   <li>/audit-log/page：T-ACCESS-007 确认前端与代码零引用后删除，查询统一 /api/perm/log/*。</li>
 *   <li>/api/perm/abstract-role/extra-roles/*：T-PERM-043 删除 GROUP_ROLE 专用写入口
 *       （add 写 abstract_user_id=null 死路径；list 恒空；读模型保留冻结）。</li>
 * </ul>
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
    "JWT_SECRET_KEY=test-jwt-secret-for-retired-endpoint",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-retired-endpoint",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-retired-endpoint"
})
class RetiredSyncTaskEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("注册表负向：全量 Context 无任何 /sync-task、/audit-log 或 extra-roles 映射")
    void handlerMappingRegistry_hasNoRetiredMappings() {
        List<String> violations = new ArrayList<>();
        // 主 MVC 注册表 + actuator ControllerEndpointHandlerMapping 都必须干净
        for (HandlerMapping mapping : applicationContext.getBeansOfType(HandlerMapping.class).values()) {
            if (!(mapping instanceof RequestMappingHandlerMapping)) {
                continue;
            }
            ((RequestMappingHandlerMapping) mapping).getHandlerMethods().forEach((info, handler) -> {
                String patterns = info.toString();
                if (patterns.contains("sync-task") || patterns.contains("audit-log")
                    || patterns.contains("extra-roles")) {
                    violations.add(patterns + " -> " + handler);
                }
            });
        }
        assertThat(violations).as("退役路径不得注册任何 Handler 映射").isEmpty();
    }

    /**
     * 运行时口径：未知路径落到 fallback resource handler，/** 拦截器链仍会执行——
     * 匿名请求先被拦截器 fail-closed 拒绝（admin 路径族 RequestContextInterceptor 401、
     * /api/perm/** 路径族内部鉴权拦截器 403，均不向匿名调用者泄露路径存在性），
     * 无拦截器覆盖的路径则 404。三者都是「无法提供服务」的明确负向结果；
     * 「无路由」的权威证据是上方注册表断言（验收 5 的第一分支）。
     */
    private static org.springframework.test.web.servlet.ResultMatcher retiredPathRejected() {
        return result -> assertThat(result.getResponse().getStatus())
            .as("退役路径匿名请求必须被拒（401/403 fail-closed 或 404），不得命中业务处理器")
            .isIn(401, 403, 404);
    }

    @Test
    @DisplayName("运行时负向：POST /sync-task/list 被拒（401/404，无业务处理器）")
    void syncTaskList_rejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/sync-task/list"))
            .andExpect(retiredPathRejected());
    }

    @Test
    @DisplayName("运行时负向：POST /sync-task/page 被拒（401/404，无业务处理器）")
    void syncTaskPage_rejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/sync-task/page")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(retiredPathRejected());
    }

    @Test
    @DisplayName("运行时负向：POST /audit-log/page 被拒（401/404，无业务处理器）")
    void auditLogPage_rejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/audit-log/page")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(retiredPathRejected());
    }

    @Test
    @DisplayName("运行时负向：POST /api/perm/abstract-role/extra-roles/list 被拒（T-PERM-043）")
    void extraRolesList_rejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/perm/abstract-role/extra-roles/list")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(retiredPathRejected());
    }

    @Test
    @DisplayName("运行时负向：POST /api/perm/abstract-role/extra-roles/add 被拒（T-PERM-043）")
    void extraRolesAdd_rejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/perm/abstract-role/extra-roles/add")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(retiredPathRejected());
    }

    @Test
    @DisplayName("运行时负向：POST /api/perm/abstract-role/extra-roles/remove 被拒（T-PERM-043）")
    void extraRolesRemove_rejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/perm/abstract-role/extra-roles/remove")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(retiredPathRejected());
    }
}
