package cn.ac.fage.accessmesh.perm.registration.config;

import cn.ac.fage.accessmesh.perm.common.feign.FeignCredentialInterceptor;
import cn.ac.fage.accessmesh.perm.registration.PermissionRegistrationPublisher;
import cn.ac.fage.accessmesh.perm.registration.RegistrationResult;
import cn.ac.fage.accessmesh.perm.registration.feign.PermissionManifestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(name="perm.registration.enabled", havingValue="true")
@EnableConfigurationProperties(PermRegistrationProperties.class)
@EnableFeignClients(clients=PermissionManifestClient.class)
@Import(FeignCredentialInterceptor.class)
public class PermRegistrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    PermissionRegistrationPublisher permissionRegistrationPublisher(PermissionManifestClient client,
            ObjectMapper json, Validator validator, PermRegistrationProperties properties) {
        properties.validateCredentialPair();
        return new PermissionRegistrationPublisher(client,json,validator,properties.declaredAllowInsecure());
    }

    @Bean
    @ConditionalOnProperty(name="perm.registration.manifest-location")
    ApplicationRunner permissionManifestStartupPublisher(PermissionRegistrationPublisher publisher,
            PermRegistrationProperties properties, ResourceLoader resources) {
        return args -> {
            var target = properties.defaultTarget();
            try (var input = resources.getResource(properties.getRegistration().getManifestLocation()).getInputStream()) {
                var snapshot = publisher.read(input);
                var result = new RegistrationResult(List.of(),publisher.publish(target,snapshot));
                if (!result.successful()) throw new IllegalStateException("startup manifest publication incomplete: " + result.manifestResult().reason());
            }
        };
    }
}
