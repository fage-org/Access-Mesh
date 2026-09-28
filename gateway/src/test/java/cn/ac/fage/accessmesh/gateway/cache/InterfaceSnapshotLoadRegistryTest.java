package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceSnapshotLoadRegistryTest {

    private final InterfaceSnapshotLoadRegistry registry = new InterfaceSnapshotLoadRegistry();

    @Test
    void shouldStartFreshLoadWhenDownstreamRetriesAfterTerminalSignal() {
        InterfaceAdmissionSnapshotResp snapshot = new InterfaceAdmissionSnapshotResp(1, 1L, null, "svc", null, null, 0L, List.of(), List.of(), "OPERATION_ADMISSION", true);
        AtomicInteger calls = new AtomicInteger();
        Supplier<Mono<InterfaceAdmissionSnapshotResp>> loader = () -> {
            if (calls.incrementAndGet() == 1) {
                return Mono.error(new RetryableLoadException());
            }
            return Mono.just(snapshot);
        };

        InterfaceAdmissionSnapshotResp result = registry.load("key-1", loader)
            .onErrorResume(RetryableLoadException.class, e -> registry.load("key-1", loader))
            .block();

        assertThat(result).isSameAs(snapshot);
        assertThat(calls).hasValue(2);
        assertThat(registry.keys()).isEmpty();
    }

    private static class RetryableLoadException extends RuntimeException {
    }
}
