package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
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
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of());
        AtomicInteger calls = new AtomicInteger();
        Supplier<Mono<InterfaceSnapshotResp>> loader = () -> {
            if (calls.incrementAndGet() == 1) {
                return Mono.error(new RetryableLoadException());
            }
            return Mono.just(snapshot);
        };

        InterfaceSnapshotResp result = registry.load("key-1", loader)
            .onErrorResume(RetryableLoadException.class, e -> registry.load("key-1", loader))
            .block();

        assertThat(result).isSameAs(snapshot);
        assertThat(calls).hasValue(2);
        assertThat(registry.keys()).isEmpty();
    }

    private static class RetryableLoadException extends RuntimeException {
    }
}
