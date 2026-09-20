package cn.ac.fage.accessmesh.access.sync.metadata;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 与 PostgreSQL TIMESTAMPTZ 微秒舍入一致的逐键版本比较。 */
public final class SyncVersionOrder {
    private SyncVersionOrder() {}
    public static LocalDateTime roundToMicros(LocalDateTime time) {
        int remainder = time.getNano() % 1000;
        LocalDateTime truncated = time.truncatedTo(ChronoUnit.MICROS);
        return remainder >= 500 ? truncated.plusNanos(1000) : truncated;
    }
    public static int compareIncoming(SyncMetadata existing, SyncVersionRef incoming) {
        if (existing == null) return 1;
        int time = roundToMicros(incoming.occurredAt()).compareTo(roundToMicros(existing.getLastSyncOccurredAt()));
        return time != 0 ? time : incoming.sequenceNo().compareTo(existing.getLastSyncSequenceNo());
    }
}
