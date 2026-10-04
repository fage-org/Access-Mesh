package cn.ac.fage.accessmesh.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;

class E2eProcessSupportTest {
    @TempDir Path directory;

    @Test
    void interruptedReadinessStopsTheUnreturnedProcess() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(15, TimeUnit.SECONDS)).thenReturn(true);
        var handle = new E2eProcessSupport.ServiceHandle(process, 12345, directory);
        assertThatThrownBy(() -> E2eProcessSupport.readyOrDestroy(handle, () -> {
            throw new InterruptedException("readiness cancelled");
        })).isInstanceOf(InterruptedException.class).hasMessage("readiness cancelled");
        verify(process).destroy();
        verify(process, never()).destroyForcibly();
    }

    @Test
    void forcedTerminationIsAwaitedWhenGracefulStopTimesOut() throws Exception {
        Process process = mock(Process.class);
        when(process.waitFor(15, TimeUnit.SECONDS)).thenReturn(false);
        when(process.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);
        new E2eProcessSupport.ServiceHandle(process, 12345, directory).destroy();
        var order = inOrder(process);
        order.verify(process).destroy();
        order.verify(process).waitFor(15, TimeUnit.SECONDS);
        order.verify(process).destroyForcibly();
        order.verify(process).waitFor(10, TimeUnit.SECONDS);
        order.verify(process).isAlive();
    }
}
