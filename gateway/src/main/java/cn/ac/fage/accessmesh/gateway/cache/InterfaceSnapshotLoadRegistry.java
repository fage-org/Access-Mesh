package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * InterfaceSnapshot 回源 in-flight 去重注册器。
 */
@Component
public class InterfaceSnapshotLoadRegistry {

    private final ConcurrentHashMap<String, Mono<InterfaceAdmissionSnapshotResp>> inFlight = new ConcurrentHashMap<>();

    public Mono<InterfaceAdmissionSnapshotResp> load(String cacheKey, Supplier<Mono<InterfaceAdmissionSnapshotResp>> loader) {
        return inFlight.computeIfAbsent(cacheKey, key -> sharedLoadMono(key, loader));
    }

    public Set<String> keys() {
        return Set.copyOf(inFlight.keySet());
    }

    private Mono<InterfaceAdmissionSnapshotResp> sharedLoadMono(String key, Supplier<Mono<InterfaceAdmissionSnapshotResp>> loader) {
        AtomicReference<Mono<InterfaceAdmissionSnapshotResp>> ref = new AtomicReference<>();
        Mono<InterfaceAdmissionSnapshotResp> mono = Mono.defer(loader)
            .doOnTerminate(() -> removeIfCurrent(key, ref.get()))
            .doOnCancel(() -> removeIfCurrent(key, ref.get()))
            .cache();
        ref.set(mono);
        return mono;
    }

    private void removeIfCurrent(String key, Mono<InterfaceAdmissionSnapshotResp> mono) {
        if (mono != null) {
            inFlight.remove(key, mono);
        }
    }
}
