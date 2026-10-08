package cn.ac.fage.accessmesh.access.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "access.platform.bootstrap")
public class PlatformBootstrapProperties {
    private boolean enabled;
    private String username = "admin";
    private String password = "";
}
