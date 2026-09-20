package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.sync.dto.*;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.perm.registration.PermissionRegistrationPublisher;
import cn.ac.fage.accessmesh.perm.registration.RegistrationTarget;
import cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Retryer;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** SDK 对真实 access-service HTTP 接口的两步协调；单服务容器测试，不启动其他业务服务。 */
@Tag("testcontainers")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker=true)
@TestPropertySource(properties={
        "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "perm.registration.enabled=false"
})
class RegistrationCoordinatorPgIT {
    private static final LocalDateTime AT=LocalDateTime.of(2026,9,21,0,0);
    private static int nextType=1800;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry,RegistrationCoordinatorPgIT.class); }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private Validator validator;
    @Autowired private ServiceCredentialDomainService credentials;

    @Test void shouldStopAfterPartialResourceHttp_thenRetryOriginalSnapshotsAndPublishManifestIndependently() throws Exception {
        Fixture f=fixture(1L);
        var publisher=publisher(); AtomicInteger reads=new AtomicInteger();
        var manifest=publisher.capture("10","dependency-snapshot",() -> { reads.incrementAndGet(); return List.of(dependency(f)); });
        var parent=new ResourceEntitySyncItem("a",null,"a",null,null,null,null,1,null,null,null,new SyncVersionRef(AT,1L));
        var child=new ResourceEntitySyncItem("b",null,"b",null,"a",null,null,1,null,null,null,new SyncVersionRef(AT,1L));
        var resources=new ResourceEntityFullSyncReq(new ResourceEntitySyncScope(f.target().serviceCode(),f.code()),List.of(parent,child),"1");
        String immutableResourceJson=json.writeValueAsString(resources);
        var first=publisher.prepareAndPublish(f.target(),manifest,target -> List.of(resourceFull(target,immutableResourceJson)));
        assertThat(first.preparationResults().getFirst().detail().failedCount()).isEqualTo(1);
        assertThat(first.manifestResult()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_dependency_declaration WHERE source_service=?",Integer.class,f.target().serviceCode())).isZero();
        var retry=publisher.prepareAndPublish(f.target(),manifest,target -> List.of(resourceFull(target,immutableResourceJson)));
        assertThat(retry.successful()).isTrue(); assertThat(reads).hasValue(1);
        assertThat(edgeCount(f)).isEqualTo(1);
        var cleared=publisher.publish(f.target(),new PermissionManifestReq(1,"11","remove-dependency",List.of()));
        assertThat(cleared.detail().deactivatedCount()).isEqualTo(1); assertThat(edgeCount(f)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resource_entity WHERE resource_type=? AND delete_flag=0",Integer.class,f.type())).isEqualTo(2);
    }

    @Test void shouldBindEachTenantCredentialOnActualHttpCalls() {
        Fixture first=fixture(1L); Fixture second=fixture(2L);
        for (Fixture f:List.of(first,second)) {
            jdbc.update("INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name) VALUES(?,?,'a','default','a'),(?,?,'b','default','b')",f.target().tenantId(),f.type(),f.target().tenantId(),f.type());
            assertThat(publisher().publish(f.target(),new PermissionManifestReq(1,"1","r",List.of(dependency(f)))).detail().failedCount()).isZero();
        }
        publisher().publish(first.target(),new PermissionManifestReq(1,"2","empty",List.of()));
        assertThat(edgeCount(first)).isZero(); assertThat(edgeCount(second)).isEqualTo(1);
    }

    @Test void shouldContinueAfterConfirmedIdenticalIncrementalPreparationRetry() throws Exception {
        Fixture f=fixture(1L); var publisher=publisher();
        var manifest=new PermissionManifestReq(1,"20","incremental",List.of(dependency(f)));
        String a=json.writeValueAsString(single(f,"a",null,"10"));
        String b=json.writeValueAsString(single(f,"b","parent","11"));
        var first=publisher.prepareAndPublish(f.target(),manifest,t -> List.of(resourceHttp(t,a,"sync"),resourceHttp(t,b,"sync")));
        assertThat(first.manifestResult()).isNull();
        assertThat(first.preparationResults().get(1).reason()).contains("PARENT");
        String parent=json.writeValueAsString(single(f,"parent",null,"12"));
        assertThat(resourceHttp(f.target(),parent,"sync").applied()).isTrue();
        var retry=publisher.prepareAndPublish(f.target(),manifest,t -> List.of(resourceHttp(t,a,"sync"),resourceHttp(t,b,"sync")));
        assertThat(retry.preparationResults().getFirst().reason()).isEqualTo("PUBLICATION_UNCHANGED");
        assertThat(retry.successful()).isTrue(); assertThat(edgeCount(f)).isEqualTo(1);
    }

    private ResourceEntitySyncReq single(Fixture f,String code,String parent,String generation) {
        return new ResourceEntitySyncReq("UPSERT",f.code(),code,null,code,null,parent,null,null,1,null,
                f.target().serviceCode(),null,null,new SyncVersionRef(AT,1L),generation);
    }

    private PermissionRegistrationPublisher publisher() {
        var client=Feign.builder().contract(new SpringMvcContract()).retryer(Retryer.NEVER_RETRY)
                .encoder((value,type,template) -> {
                    try { template.header("Content-Type","application/json"); template.body(json.writeValueAsString(value)); }
                    catch (java.io.IOException e) { throw new feign.codec.EncodeException("encode failed",e); }
                })
                .decoder((response,type) -> json.readValue(response.body().asInputStream(),json.constructType(type)))
                .target(PermissionManifestClient.class,"http://localhost:"+port);
        return new PermissionRegistrationPublisher(client,json,validator,true);
    }
    private SyncResultResp resourceFull(RegistrationTarget target,String body) {
        return resourceHttp(target,body,"full-sync");
    }
    private SyncResultResp resourceHttp(RegistrationTarget target,String body,String operation) {
        try (var client=HttpClient.newHttpClient()) {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/access/resource-entity/"+operation))
                    .header("Content-Type","application/json").header("X-Tenant-Id",target.tenantId().toString())
                    .header("X-Service-Code",target.serviceCode()).header("X-Credential-Id",target.credentialId())
                    .header("X-Credential-Secret",target.credentialSecret()).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var envelope=json.readTree(response.body()); assertThat(envelope.path("code").asInt()).isEqualTo(200);
            return json.treeToValue(envelope.path("data"),SyncResultResp.class);
        } catch (Exception e) { throw new IllegalStateException("resource preparation HTTP failed",e); }
    }
    private Dependency dependency(Fixture f) { return new Dependency("a-needs-b",new ResourceKey(f.code(),"a",null),List.of("VIEW"),List.of(new Requirement(new ResourceKey(f.code(),"b",null),List.of("VIEW"))),null); }
    private int edgeCount(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM resource_dependency WHERE tenant_id=? AND owner_service_code=? AND delete_flag=0",Integer.class,f.target().tenantId(),f.target().serviceCode()); }
    private Fixture fixture(Long tenant) {
        int type=nextType++; String code="SDK_"+type; String source="sdk-"+UUID.randomUUID();
        jdbc.update("INSERT INTO service_config(tenant_id,service_code,name) VALUES(?,?,'sdk')",tenant,source);
        jdbc.update("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name,extra) VALUES(?,'resource_type',?,?,'sdk',CAST(? AS jsonb))",tenant,code,type,"{\"managedMode\":\"SYNC\",\"syncSourceService\":\""+source+"\"}");
        jdbc.update("INSERT INTO operation_permission(tenant_id,resource_type,code,name,binary_bit,inherit_mask) VALUES(?,?,'VIEW','View',2,0)",tenant,type);
        var credential=credentials.issue(tenant,source,null,100L);
        return new Fixture(new RegistrationTarget(tenant,source,credential.entity().getCredentialId(),credential.plainSecret()),code,type);
    }
    private record Fixture(RegistrationTarget target,String code,int type) {}
}
