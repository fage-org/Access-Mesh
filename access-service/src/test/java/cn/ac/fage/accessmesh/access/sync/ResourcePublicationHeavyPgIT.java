package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资源发布超大规模清理验证（从 {@link ResourcePublicationPgIT} 拆档，T-PERM-079）。
 * <p>
 * 单用例灌 65540 行资源+metadata 做全量差异清理，是容器组最重用例——挂
 * {@code testcontainers-heavy} 标签：日常形态 {@code -DskipHeavyIT=true} 跳过、
 * 收口形态 {@code mvn test -T 1C} 必跑（拆档机制见 .claude/rules/testing-standards.md §10）。
 * </p>
 */
@Tag("testcontainers")
@Tag("testcontainers-heavy")
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false"
})
class ResourcePublicationHeavyPgIT {
    private static int nextType = 1300;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, ResourcePublicationHeavyPgIT.class); }
    @Autowired private ResourceEntitySyncAppService service;
    @Autowired private JdbcTemplate jdbc;
    @AfterEach void clear() { AccessRequestContext.clear(); }

    @Test void shouldClearLargeOwnedScope_withoutExceedingDatabaseParameterLimit() {
        Fixture f = fixture();
        int size = 65540;
        jdbc.update("""
                INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name)
                SELECT 1,?,'bulk-' || i,'default','bulk' FROM generate_series(1,?) i
                """, f.type(), size);
        String scope = SyncKeyCodecUtil.resourceEntityScopeKey(f.typeCode());
        String businessTemplate = SyncKeyCodecUtil.resourceEntityBusinessKey(f.typeCode(), "__KEY__", "default");
        String syncTemplate = SyncKeyCodecUtil.syncKey(f.service(), "RESOURCE_ENTITY", "__BUSINESS__");
        jdbc.update("""
                WITH facts AS (
                    SELECT id,replace(?, '__KEY__', code) AS business_key FROM resource_entity WHERE tenant_id=1 AND resource_type=?
                ), encoded AS (
                    SELECT id,business_key,replace(?, '__BUSINESS__', business_key) AS sync_key FROM facts
                )
                INSERT INTO sync_metadata(tenant_id,entity_kind,source_service,scope_key,scope_key_hash,
                    business_key,business_key_hash,sync_key,sync_key_hash,target_id,target_status,last_sync_occurred_at,last_sync_sequence_no)
                SELECT 1,'RESOURCE_ENTITY',?,?,?,business_key,encode(sha256(convert_to(business_key,'UTF8')),'hex'),
                    sync_key,encode(sha256(convert_to(sync_key,'UTF8')),'hex'),id,'ACTIVE',now(),1 FROM encoded
                """, businessTemplate, f.type(), syncTemplate, f.service(), scope, SyncKeyCodecUtil.sha256Hex(scope));
        assertThat(full(f, "1", List.of()).detail().deactivatedCount()).isEqualTo(size);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE resource_type=? AND delete_flag=0", Integer.class, f.type())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_metadata WHERE source_service=? AND target_status='DELETED'", Integer.class, f.service())).isEqualTo(size);
    }

    private SyncResultResp full(Fixture f, String generation, List<ResourceEntitySyncItem> items) {
        AccessRequestContext.bind(RequestContext.service(1L, f.service()));
        try { return service.fullSync(1L, new ResourceEntityFullSyncReq(new ResourceEntitySyncScope(f.service(), f.typeCode()), items, generation), null); }
        finally { AccessRequestContext.clear(); }
    }
    private Fixture fixture() {
        int type = nextType++;
        String source = "publication-" + UUID.randomUUID();
        String code = "PUB_" + type;
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES (1,?,'publication')", source);
        jdbc.update("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name,extra) VALUES (1,'resource_type',?,?,'publication',CAST(? AS jsonb))",
                code, type, "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"" + source + "\"}");
        return new Fixture(source, code, type);
    }
    private record Fixture(String service, String typeCode, int type) {}
}
