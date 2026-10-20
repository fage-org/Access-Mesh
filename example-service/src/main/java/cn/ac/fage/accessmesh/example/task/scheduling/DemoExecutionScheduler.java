package cn.ac.fage.accessmesh.example.task.scheduling;

import cn.ac.fage.accessmesh.example.task.service.DemoExecutionAppService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Single-instance worker; recovery completes before any new claim is allowed. */
@Component
@ConditionalOnProperty(name="example.execution.enabled",havingValue="true",matchIfMissing=true)
public class DemoExecutionScheduler {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DemoExecutionScheduler.class);
    private final DemoExecutionAppService service;
    private final cn.ac.fage.accessmesh.example.task.service.DemoTaskAppService tasks;
    private boolean ready;
    public DemoExecutionScheduler(DemoExecutionAppService service,cn.ac.fage.accessmesh.example.task.service.DemoTaskAppService tasks) { this.service=service; this.tasks=tasks; }
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() { tasks.resetSchedule(); service.recover(); ready=true; }
    @Scheduled(fixedDelay=1000)
    public synchronized void tick() {
        if(!ready) return;
        try { service.expire(); tasks.scheduleNext(); service.runNext(); }
        catch(RuntimeException e) { log.warn("Snapshot worker cycle failed ({})",e.getClass().getSimpleName()); }
    }
}
