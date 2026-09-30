package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.characterization.R2BaselineFixture;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("testcontainers")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false",
    "access.bootstrap.enabled=false", "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN", "PERM_INTERNAL_SECRET=budget-it-internal",
    "accessmesh.query.limits.max-items=1"
})
class QueryBudgetPgIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, QueryBudgetPgIT.class); }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void should_failWholeHttpBatchAndRecoverNextRequest_whenConfiguredBudgetIsExceeded() throws Exception {
        new R2BaselineFixture(jdbc).seedBaselineGraph();
        var item = Map.of("resourceTypeCode", R2BaselineFixture.TYPE_T1_CODE,
            "resourceCode", R2BaselineFixture.CODE_R1, "operationCode", "VIEW");
        var single = Map.of("subjectTypeCode", "USER", "subjectExternalId", String.valueOf(R2BaselineFixture.USER_INST),
            "items", List.of(item));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/access/auth/batch-check").header("X-Tenant-Id", "1")
                    .header("X-Internal-Secret", "budget-it-internal").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(single)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].allowed").value(true));
            var excessive = Map.of("subjectTypeCode", "USER", "subjectExternalId", String.valueOf(R2BaselineFixture.USER_INST),
                "items", List.of(item, item));
            mvc.perform(post("/api/access/auth/batch-check").header("X-Tenant-Id", "1")
                    .header("X-Internal-Secret", "budget-it-internal").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(excessive)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(99999))
                .andExpect(jsonPath("$.data").doesNotExist());
        }
    }
}
