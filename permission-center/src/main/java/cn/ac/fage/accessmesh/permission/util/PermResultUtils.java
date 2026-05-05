package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;

import java.util.*;

/**
 * Static conversion methods: PermResult → various response DTOs.
 * Callers use these after {@code engine.query()} returns.
 */
public final class PermResultUtils {

    private static final int PERM_DENIED_CODE = 403;

    private PermResultUtils() {}

    // ===== validation =====

    /** Throw BizException(403, reason) if not allowed. */
    public static void validateOrThrow(PermResult result) {
        if (!result.allowed()) {
            throw new BizException(PERM_DENIED_CODE,
                result.reason() != null ? result.reason() : "PERMISSION_DENIED");
        }
    }

    /** Extract IDs that were denied from a batch check result map. */
    public static <ID> Set<ID> getDeniedIds(Map<ID, PermResult> results) {
        Set<ID> denied = new LinkedHashSet<>();
        for (var entry : results.entrySet()) {
            if (!entry.getValue().allowed()) {
                denied.add(entry.getKey());
            }
        }
        return denied;
    }

    // ===== DTO conversions =====

    /** PermResult → AuthCheckResp */
    public static AuthCheckResp toAuthCheckResp(PermResult r) {
        if (!r.allowed()) {
            return AuthCheckResp.deny(r.reason() != null ? r.reason() : "DENIED");
        }
        boolean condEvaluated = r.allEntries().stream().anyMatch(RolePermEntry::hasCondition);
        return AuthCheckResp.allow(
            r.matchedRoleIds().stream().toList(),
            r.matchedPermissionIds().stream().toList(), condEvaluated);
    }

    /** Map<resourceCode, PermResult> → BatchAuthCheckResp */
    public static BatchAuthCheckResp toBatchAuthCheckResp(
            Map<String, PermResult> resultsByResourceCode) {
        List<BatchAuthCheckResp.AuthCheckItemResult> items = new ArrayList<>();
        for (var entry : resultsByResourceCode.entrySet()) {
            PermResult r = entry.getValue();
            AuthCheckResp a = toAuthCheckResp(r);
            items.add(new BatchAuthCheckResp.AuthCheckItemResult(
                null, entry.getKey(), null, a.allowed(), a.reason(),
                a.matchedRoleIds(), a.matchedPermissionIds()));
        }
        return new BatchAuthCheckResp(List.copyOf(items));
    }

    /** PermResult → CheckInterfaceResp */
    public static CheckInterfaceResp toCheckInterfaceResp(PermResult r, int cacheTtlSeconds) {
        Map<Long, ResourceEntity> resMap = r.resourceMap();
        Map<Long, OperationPermission> opMap = r.operationMap();

        List<CheckInterfaceResp.MatchedResource> matched = new ArrayList<>();
        if (resMap != null && opMap != null) {
            Map<Long, List<RolePermEntry>> byResource = new LinkedHashMap<>();
            for (RolePermEntry e : r.allEntries()) {
                if (e.resourceEntityId() != null) {
                    byResource.computeIfAbsent(e.resourceEntityId(), k -> new ArrayList<>()).add(e);
                }
            }
            for (var entry : byResource.entrySet()) {
                ResourceEntity res = resMap.get(entry.getKey());
                List<RolePermEntry> perms = entry.getValue();
                if (perms.isEmpty()) continue;
                boolean allowed = true;
                OperationPermission op = opMap.get(perms.get(0).operationPermissionId());
                String opCode = op != null ? op.getCode() : null;
                List<Long> roleIds = perms.stream().map(RolePermEntry::roleId).filter(Objects::nonNull).distinct().toList();
                List<Long> permIds = perms.stream().map(RolePermEntry::permissionId).filter(Objects::nonNull).distinct().toList();
                matched.add(new CheckInterfaceResp.MatchedResource(
                    res != null ? res.getId() : entry.getKey(),
                    null,  // resourceTypeCode filled by caller
                    res != null ? res.getCode() : null,
                    opCode, allowed, roleIds, permIds));
            }
        }
        return r.allowed()
            ? CheckInterfaceResp.allow(matched, cacheTtlSeconds)
            : CheckInterfaceResp.deny(r.reason() != null ? r.reason() : "DENIED", matched, cacheTtlSeconds);
    }

    /** PermResult → QueryResourcesResp */
    public static QueryResourcesResp toQueryResourcesResp(PermResult r, int cacheTtlSeconds) {
        Map<Long, ResourceEntity> resMap = r.resourceMap();
        if (resMap == null) return new QueryResourcesResp(List.of(), null, cacheTtlSeconds);

        Map<Long, List<RolePermEntry>> byResource = new LinkedHashMap<>();
        for (RolePermEntry e : r.allEntries()) {
            if (e.resourceEntityId() != null) {
                byResource.computeIfAbsent(e.resourceEntityId(), k -> new ArrayList<>()).add(e);
            }
        }
        Map<Long, OperationPermission> opMap = r.operationMap();
        List<QueryResourcesResp.ResourceEntry> entries = new ArrayList<>();
        for (var entry : byResource.entrySet()) {
            ResourceEntity res = resMap.get(entry.getKey());
            if (res == null) continue;
            List<RolePermEntry> perms = entry.getValue();
            List<String> ops = perms.stream()
                .map(e -> opMap != null ? opMap.get(e.operationPermissionId()) : null)
                .filter(Objects::nonNull).map(OperationPermission::getCode).distinct().toList();
            List<Long> roleIds = perms.stream().map(RolePermEntry::roleId).filter(Objects::nonNull).distinct().toList();
            List<Long> permIds = perms.stream().map(RolePermEntry::permissionId).filter(Objects::nonNull).distinct().toList();
            List<String> sources = perms.stream().map(RolePermEntry::grantSource).filter(Objects::nonNull).distinct().toList();
            entries.add(new QueryResourcesResp.ResourceEntry(
                null,  // resourceTypeCode filled by caller
                res.getCode(), res.getCodeType(), res.getName(),
                perms.stream().anyMatch(e -> Boolean.TRUE.equals(e.canGrant())),
                ops, roleIds, permIds, sources));
        }
        return new QueryResourcesResp(entries, null, cacheTtlSeconds);
    }
}
