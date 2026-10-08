package cn.ac.fage.accessmesh.access.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "access.platform.bootstrap", name = "enabled", havingValue = "true")
public class PlatformBootstrapRunner implements ApplicationRunner {
    private final PlatformBootstrapProperties properties;
    private final PlatformBootstrapInitializer initializer;
    public PlatformBootstrapRunner(PlatformBootstrapProperties properties, PlatformBootstrapInitializer initializer) {
        this.properties = properties;
        this.initializer = initializer;
    }
    @Override
    public void run(ApplicationArguments args) {
        initializer.initialize(properties.getUsername(), properties.getPassword());
    }
}
