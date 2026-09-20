package cn.ac.fage.accessmesh.perm.registration;

import cn.ac.fage.accessmesh.perm.registration.config.PermRegistrationAutoConfiguration;
import cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient;
import cn.ac.fage.accessmesh.perm.common.feign.FeignCredentialInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermRegistrationAutoConfigurationTest {
    private static final jakarta.validation.ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    @TempDir Path temporary;
    @AfterAll static void close() { VALIDATORS.close(); }
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,PermRegistrationAutoConfiguration.class))
            .withBean(ObjectMapper.class,ObjectMapper::new)
            .withBean(Validator.class,VALIDATORS::getValidator)
            .withBean("testTransport",PermissionManifestClient.class,() -> mock(PermissionManifestClient.class),d -> d.setPrimary(true));

    @Test void shouldRemainInactiveWithoutOptIn() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(PermissionRegistrationPublisher.class);
            assertThat(context).doesNotHaveBean(FeignCredentialInterceptor.class);
        });
    }
    @Test void shouldImportExistingCredentialInterceptorWithPublisher() {
        runner.withPropertyValues("perm.registration.enabled=true","perm.allow-insecure=true",
                "perm.credential-id=id","perm.credential-secret=secret").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PermissionRegistrationPublisher.class);
            assertThat(context).hasSingleBean(FeignCredentialInterceptor.class);
            assertThat(context).doesNotHaveBean("permissionManifestStartupPublisher");
        });
    }
    @Test void shouldRejectHalfCredentialOrMissingTrustDeclaration() {
        for (String[] properties : new String[][]{
                {"perm.registration.enabled=true","perm.allow-insecure=true","perm.credential-secret=secret"},
                {"perm.registration.enabled=true"},
                {"perm.registration.enabled=true","perm.allow-insecure=flase"},
                {"perm.registration.enabled=true","perm.allow-insecure=yes"},
                {"perm.registration.enabled=true","perm.allow-insecure=on"},
                {"perm.registration.enabled=true","perm.allow-insecure=1"}}) {
            runner.withPropertyValues(properties).run(context -> assertThat(context).hasFailed());
        }
    }
    @Test void shouldRunStaticFilePublicationOnceWithItsBoundGeneration() throws Exception {
        Path file=temporary.resolve("manifest.json");
        Files.writeString(file,"{\"schemaVersion\":1,\"publicationGeneration\":\"42\",\"revision\":\"file-42\",\"dependencies\":[]}");
        staticRunner(file).run(context -> {
            assertThat(context).hasNotFailed();
            var client=context.getBean("testTransport",PermissionManifestClient.class);
            when(client.fullSync(anyLong(),anyString(),anyString(),anyString(),any())).thenReturn(R.ok(
                    new SyncResultResp(true,true,false,null,null,new SyncResultResp.FullSyncDetail(0,0,0,0,List.of()))));
            context.getBean("permissionManifestStartupPublisher",ApplicationRunner.class).run(new DefaultApplicationArguments());
            verify(client).fullSync(eq(3L),eq("reports"),eq("id"),eq("secret"),argThat(req -> req.publicationGeneration().equals("42") && req.revision().equals("file-42")));
        });
    }
    @Test void shouldRejectReadFailureAndPartialResultDuringStartup() throws Exception {
        Path file=temporary.resolve("manifest.json");
        staticRunner(file).run(context -> {
            var startup=context.getBean("permissionManifestStartupPublisher",ApplicationRunner.class);
            assertThatThrownBy(() -> startup.run(new DefaultApplicationArguments())).isInstanceOf(java.io.IOException.class);
            verifyNoInteractions(context.getBean("testTransport",PermissionManifestClient.class));
        });
        Files.writeString(file,"{\"schemaVersion\":1,\"publicationGeneration\":\"42\",\"revision\":\"r\",\"dependencies\":[]}");
        staticRunner(file).run(context -> {
            when(context.getBean("testTransport",PermissionManifestClient.class).fullSync(anyLong(),anyString(),anyString(),anyString(),any())).thenReturn(R.ok(
                    new SyncResultResp(false,false,false,"RETRYABLE","FULL_SYNC_PARTIAL_FAILURE",new SyncResultResp.FullSyncDetail(0,0,1,0,List.of()))));
            assertThatThrownBy(() -> context.getBean("permissionManifestStartupPublisher",ApplicationRunner.class).run(new DefaultApplicationArguments()))
                    .hasMessageContaining("publication incomplete");
        });
    }
    private ApplicationContextRunner staticRunner(Path file) {
        return runner.withPropertyValues("perm.registration.enabled=true","perm.allow-insecure=true",
                "perm.tenant-id=3","perm.service-code=reports","perm.credential-id=id","perm.credential-secret=secret",
                "perm.registration.manifest-location="+file.toUri());
    }
}
