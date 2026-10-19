package cn.ac.fage.accessmesh.example.metadata.publication;

import cn.ac.fage.accessmesh.example.metadata.service.DemoPublicationAppService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name="example.publication.enabled", havingValue="true", matchIfMissing=true)
public class DemoPublicationScheduler {
    private static final Logger log = LoggerFactory.getLogger(DemoPublicationScheduler.class);
    private final DemoPublicationAppService service;
    public DemoPublicationScheduler(DemoPublicationAppService service) { this.service = service; }
    @Scheduled(fixedDelayString="${example.publication.poll-delay-ms:1000}")
    public void publish() {
        try { service.publishNext(); }
        catch (RuntimeException e) {
            log.warn("Resource publication cycle failed ({})", e.getClass().getSimpleName());
        }
    }
}
