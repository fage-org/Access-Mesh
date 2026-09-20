package cn.ac.fage.accessmesh.access.sync.metadata;

import java.util.Objects;

/** 资源范围 FULL 屏障与逐键增量顺序；范围最大值不用于拒绝其他键的晚到增量。 */
public final class ResourcePublicationPolicy {
    private ResourcePublicationPolicy() {}
    public enum Decision { APPLY, UNCHANGED, STALE, CONFLICT, GENERATION_REQUIRED }

    public static Decision full(ResourcePublicationState state, long generation, String hash) {
        if (state == null) return Decision.APPLY;
        if (generation < state.getMaxGeneration()) return Decision.STALE;
        if (Objects.equals(state.getLastFullGeneration(), generation)) {
            return Objects.equals(state.getLastFullPayloadHash(), hash) ? Decision.APPLY : Decision.CONFLICT;
        }
        return generation <= state.getMaxGeneration() ? Decision.STALE : Decision.APPLY;
    }

    public static Decision single(ResourcePublicationState state, SyncMetadata metadata, Long generation, String hash) {
        if (generation == null) return state == null ? Decision.APPLY : Decision.GENERATION_REQUIRED;
        if (state != null && state.getLastFullGeneration() != null && generation <= state.getLastFullGeneration()) {
            return Decision.STALE;
        }
        if (metadata == null || metadata.getLastPublicationGeneration() == null) return Decision.APPLY;
        int comparison = generation.compareTo(metadata.getLastPublicationGeneration());
        if (comparison < 0) return Decision.STALE;
        if (comparison == 0) return Objects.equals(hash, metadata.getLastPublicationHash()) ? Decision.UNCHANGED : Decision.CONFLICT;
        return Decision.APPLY;
    }
}
