package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;

import java.util.*;

/**
 * Static utilities for OperationPermission bit-matching and batch filtering.
 */
public final class OperationPermissionUtils {

    private OperationPermissionUtils() {}

    // ===== effective bits =====

    /** Effective permission bits = binaryBit | inheritMask. */
    public static long effectiveBits(OperationPermission op) {
        return effectiveBits(op.getBinaryBit(), op.getInheritMask());
    }

    public static long effectiveBits(Long binaryBit, Long inheritMask) {
        long b = binaryBit == null ? 0L : binaryBit;
        long i = inheritMask == null ? 0L : inheritMask;
        return b | i;
    }

    // ===== covers / matches =====

    /** Whether {@code granted} operation covers {@code target} operation. */
    public static boolean covers(OperationPermission granted, OperationPermission target) {
        if (granted == null || target == null) return false;
        Long targetBit = target.getBinaryBit();
        if (targetBit == null || targetBit == 0L) return false;
        return (effectiveBits(granted) & targetBit) != 0L;
    }

    // ===== batch filtering =====

    /**
     * Filter entries whose granted operation covers the target operation.
     */
    public static List<RolePermEntry> filterByOperation(
            List<RolePermEntry> entries,
            Map<Long, OperationPermission> opCache,
            OperationPermission targetOp) {
        if (entries.isEmpty() || targetOp == null) return List.of();
        List<RolePermEntry> result = new ArrayList<>();
        for (RolePermEntry e : entries) {
            OperationPermission granted = opCache.get(e.operationPermissionId());
            if (covers(granted, targetOp)) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Group entries by target operation ID, filtering by coverage.
     */
    public static Map<Long, List<RolePermEntry>> filterByOperations(
            List<RolePermEntry> entries,
            Map<Long, OperationPermission> opCache,
            Set<Long> targetOpIds) {
        if (entries.isEmpty() || targetOpIds == null || targetOpIds.isEmpty()) return Map.of();
        Map<Long, List<RolePermEntry>> result = new LinkedHashMap<>();
        for (Long tid : targetOpIds) {
            OperationPermission target = opCache.get(tid);
            if (target == null) continue;
            List<RolePermEntry> matched = filterByOperation(entries, opCache, target);
            if (!matched.isEmpty()) {
                result.put(tid, matched);
            }
        }
        return result;
    }
}
