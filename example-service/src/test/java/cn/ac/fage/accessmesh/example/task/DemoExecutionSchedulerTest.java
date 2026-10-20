package cn.ac.fage.accessmesh.example.task;

import cn.ac.fage.accessmesh.example.task.scheduling.DemoExecutionScheduler;
import cn.ac.fage.accessmesh.example.task.service.DemoExecutionAppService;
import cn.ac.fage.accessmesh.example.task.service.DemoTaskAppService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoExecutionSchedulerTest {
    @Test void shouldBlockBothTriggersAndClaimsUntilStartupRecoveryCompletes() {
        var executions=mock(DemoExecutionAppService.class); var tasks=mock(DemoTaskAppService.class);
        var scheduler=new DemoExecutionScheduler(executions,tasks);
        scheduler.tick(); verifyNoInteractions(executions,tasks);
        doThrow(new IllegalStateException("database unavailable")).when(executions).recover();
        assertThrows(IllegalStateException.class,scheduler::start); scheduler.tick();
        verify(tasks,never()).scheduleNext(); verify(executions,never()).runNext();
        doNothing().when(executions).recover(); scheduler.start(); scheduler.tick();
        verify(tasks).scheduleNext(); verify(executions).runNext(); verify(executions).expire();
    }
}
