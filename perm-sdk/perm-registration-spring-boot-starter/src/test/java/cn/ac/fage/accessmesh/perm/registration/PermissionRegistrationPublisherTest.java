package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.*;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PermissionRegistrationPublisherTest {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final PermissionManifestClient client = mock(PermissionManifestClient.class);
    private final PermissionRegistrationPublisher publisher = new PermissionRegistrationPublisher(client,new ObjectMapper(),VALIDATORS.getValidator(),true);
    private final RegistrationTarget target = new RegistrationTarget(1L,"reports","id-one","secret-one");
    @AfterAll static void close() { VALIDATORS.close(); }

    @Test void shouldCaptureOnceAndRetryTheSameImmutablePublication() {
        var dependencies = new ArrayList<>(List.of(dependency("ab","a","b")));
        AtomicInteger reads = new AtomicInteger();
        var snapshot = publisher.capture("42","revision",() -> { reads.incrementAndGet(); return dependencies; });
        dependencies.clear();
        when(client.fullSync(anyLong(),anyString(),anyString(),anyString(),any())).thenReturn(R.ok(success()));
        publisher.publish(target,snapshot); publisher.publish(target,snapshot);
        assertThat(reads).hasValue(1); assertThat(snapshot.dependencies()).hasSize(1);
        verify(client,times(2)).fullSync(1L,"reports","id-one","secret-one",snapshot);
    }

    @Test void shouldNeverTurnProviderFailureIntoEmptyPublication() {
        assertThatThrownBy(() -> publisher.capture("1","r",() -> { throw new IllegalStateException("source unavailable"); })).hasMessage("source unavailable");
        assertThatThrownBy(() -> publisher.capture("1","r",() -> null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    @Test void shouldRejectMalformedInputBeforeResourcePreparation() {
        AtomicInteger preparations = new AtomicInteger();
        for (var input : List.of(new PermissionManifestReq(1,"01","r",List.of()),
                new PermissionManifestReq(1,"9223372036854775808","r",List.of()),
                new PermissionManifestReq(1,"1","r",List.of(dependency("self","a","a"))),
                new PermissionManifestReq(1,"1","r",List.of(dependency("x","a","b"),dependency("x","b","c"))),
                new PermissionManifestReq(1,"1","r",List.of(dependency("ab","a","b"),dependency("ba","b","a"))))) {
            assertThatThrownBy(() -> publisher.prepareAndPublish(target,input,t -> { preparations.incrementAndGet(); return List.of(); }))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(preparations).hasValue(0); verifyNoInteractions(client);
    }

    @Test void shouldStopOnPartialResourcePreparation_thenAllowExplicitOriginalRetry() {
        var snapshot = publisher.capture("42","original",() -> List.of(dependency("ab","a","b")));
        var partial = new SyncResultResp(true,false,false,"RETRYABLE","FULL_SYNC_PARTIAL_FAILURE",
                new SyncResultResp.FullSyncDetail(1,0,1,0,List.of(new SyncResultResp.ItemResult("b",false,false,"DEPENDENCY_MISSING","parent"))));
        var first = publisher.prepareAndPublish(target,snapshot,t -> List.of(partial));
        assertThat(first.successful()).isFalse(); assertThat(first.manifestResult()).isNull();
        assertThat(first.preparationResults()).containsExactly(partial); verifyNoInteractions(client);
        when(client.fullSync(anyLong(),anyString(),anyString(),anyString(),same(snapshot))).thenReturn(R.ok(success()));
        assertThat(publisher.prepareAndPublish(target,snapshot,t -> List.of(success())).successful()).isTrue();
        verify(client).fullSync(1L,"reports","id-one","secret-one",snapshot);
    }

    @Test void shouldStopExpiredResourceSnapshot_andNotTreatManifestPartialAsSuccess() {
        var snapshot = new PermissionManifestReq(1,"1","r",List.of());
        var stale = new SyncResultResp(true,false,true,"STALE_VERSION","PUBLICATION_GENERATION_STALE",
                new SyncResultResp.FullSyncDetail(0,0,0,0,List.of()));
        assertThat(publisher.prepareAndPublish(target,snapshot,t -> List.of(stale)).successful()).isFalse();
        verifyNoInteractions(client);
        var failed = new SyncResultResp(false,false,false,"RETRYABLE","FULL_SYNC_PARTIAL_FAILURE",
                new SyncResultResp.FullSyncDetail(0,0,1,0,List.of(new SyncResultResp.ItemResult("d",false,false,"NON_RETRYABLE","CROSS_OWNER"))));
        when(client.fullSync(anyLong(),anyString(),anyString(),anyString(),any())).thenReturn(R.ok(failed));
        assertThat(publisher.prepareAndPublish(target,snapshot,t -> List.of()).successful()).isFalse();
    }

    @Test void shouldReadStrictStaticSnapshotAndKeepExplicitEmptyManifest() throws Exception {
        var snapshot = publisher.read(stream("{\"schemaVersion\":1,\"publicationGeneration\":\"42\",\"revision\":\"r\",\"dependencies\":[]}"));
        assertThat(snapshot.dependencies()).isEmpty(); assertThat(snapshot.publicationGeneration()).isEqualTo("42");
        assertThatThrownBy(() -> publisher.read(stream("{\"schemaVersion\":1,\"publicationGeneration\":\"42\",\"revision\":\"r\"}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.read(stream("{\"autoGrant\":false}"))).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> publisher.read(stream("{"))).isInstanceOf(java.io.IOException.class);
        String complete = "{\"schemaVersion\":1,\"publicationGeneration\":\"42\",\"revision\":\"r\",\"dependencies\":[]}";
        assertThatThrownBy(() -> publisher.read(stream(complete + " {}"))).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> publisher.read(stream(complete + " garbage"))).isInstanceOf(java.io.IOException.class);
        verifyNoInteractions(client);
    }

    @Test void shouldKeepTenantCredentialAndGenerationBoundToEachSnapshot() {
        var one = new TenantRegistrationProvider.TenantPublication(target,new PermissionManifestReq(1,"10","a",List.of()));
        var other = new RegistrationTarget(2L,"reports","id-two","secret-two");
        var two = new TenantRegistrationProvider.TenantPublication(other,new PermissionManifestReq(1,"20","b",List.of()));
        var publications = publisher.capture(() -> List.of(one,two));
        when(client.fullSync(anyLong(),anyString(),anyString(),anyString(),any())).thenReturn(R.ok(success()));
        for (var item : publications) publisher.publish(item.target(),item.manifest());
        verify(client).fullSync(1L,"reports","id-one","secret-one",one.manifest());
        verify(client).fullSync(2L,"reports","id-two","secret-two",two.manifest());
        assertThat(target.toString()).doesNotContain("secret-one","id-one");
        assertThatThrownBy(() -> publisher.capture(() -> List.of(one,one))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void shouldRejectErrorEnvelopesAndMissingTrustDeclaration() {
        when(client.fullSync(anyLong(),anyString(),anyString(),anyString(),any())).thenReturn(R.fail(20066,"expired"));
        assertThatThrownBy(() -> publisher.publish(target,new PermissionManifestReq(1,"1","r",List.of())))
                .hasMessageContaining("20066");
        assertThatThrownBy(() -> new PermissionRegistrationPublisher(client,new ObjectMapper(),VALIDATORS.getValidator(),null))
                .hasMessageContaining("allow-insecure");
    }
    private static Dependency dependency(String key,String from,String to) {
        return new Dependency(key,new ResourceKey("REPORT",from,null),List.of("VIEW"),
                List.of(new Requirement(new ResourceKey("REPORT",to,null),List.of("VIEW"))),null);
    }
    private static SyncResultResp success() { return new SyncResultResp(true,true,false,null,null,new SyncResultResp.FullSyncDetail(0,0,0,0,List.of())); }
    private static ByteArrayInputStream stream(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
}
