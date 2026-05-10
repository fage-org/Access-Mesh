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
 * 权限结果转换工具类
 * <p>
 * 提供PermResult转换为各种响应DTO的静态方法。
 * 在engine.query()返回结果后使用这些方法进行响应转换。
 * </p>
 */
public final class PermResultUtils {

    /**
     * 权限拒绝错误码
     */
    private static final int PERM_DENIED_CODE = 403;

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private PermResultUtils() {}

    // ===== 权限校验 =====

    /**
     * 校验权限结果，不通过则抛出异常
     * <p>
     * 如果权限结果为不允许，抛出BizException(403, reason)。
     * 用于强制权限校验场景。
     * </p>
     *
     * @param result 权限查询结果
     * @throws BizException 如果权限不通过
     */
    public static void validateOrThrow(PermResult result) {
        if (!result.allowed()) {
            throw new BizException(PERM_DENIED_CODE,
                result.reason() != null ? result.reason() : "PERMISSION_DENIED");
        }
    }

    /**
     * 提取批量校验结果中被拒绝的ID集合
     * <p>
     * 从批量权限校验结果映射中提取所有被拒绝的ID。
     * 用于批量权限判定后筛选拒绝项。
     * </p>
     *
     * @param results 批量权限校验结果映射
     * @return 被拒绝的ID集合
     */
    public static <ID> Set<ID> getDeniedIds(Map<ID, PermResult> results) {
        Set<ID> denied = new LinkedHashSet<>();
        for (var entry : results.entrySet()) {
            if (!entry.getValue().allowed()) {
                denied.add(entry.getKey());
            }
        }
        return denied;
    }

    // ===== DTO转换 =====

    /**
     * 转换PermResult为AuthCheckResp
     * <p>
     * 将权限查询结果转换为权限校验响应DTO。
     * 包含匹配的角色ID、权限ID和条件评估状态。
     * </p>
     *
     * @param r 权限查询结果
     * @return 权限校验响应
     */
    public static AuthCheckResp toAuthCheckResp(PermResult r) {
        if (!r.allowed()) {
            return AuthCheckResp.deny(r.reason() != null ? r.reason() : "DENIED");
        }
        boolean condEvaluated = r.allEntries().stream().anyMatch(RolePermEntry::hasCondition);
        return AuthCheckResp.allow(
            r.matchedRoleIds().stream().toList(),
            r.matchedPermissionIds().stream().toList(), condEvaluated);
    }

    /**
     * 转换资源编码权限结果映射为BatchAuthCheckResp
     * <p>
     * 将按资源编码分组的权限查询结果转换为批量权限校验响应DTO。
     * </p>
     *
     * @param resultsByResourceCode 按资源编码分组的权限结果映射
     * @return 批量权限校验响应
     */
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

    /**
     * 转换PermResult为CheckInterfaceResp
     * <p>
     * 将权限查询结果转换为接口校验响应DTO。
     * 包含匹配的资源信息、操作码和角色权限ID。
     * 用于Gateway接口权限校验场景。
     * </p>
     *
     * @param r              权限查询结果
     * @param cacheTtlSeconds 缓存有效期（秒）
     * @return 接口校验响应
     */
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
                    null,  // resourceTypeCode 由调用方填充
                    res != null ? res.getCode() : null,
                    opCode, allowed, roleIds, permIds));
            }
        }
        return r.allowed()
            ? CheckInterfaceResp.allow(matched, cacheTtlSeconds)
            : CheckInterfaceResp.deny(r.reason() != null ? r.reason() : "DENIED", matched, cacheTtlSeconds);
    }

    /**
     * 转换PermResult为QueryResourcesResp
     * <p>
     * 将权限查询结果转换为资源查询响应DTO。
     * 包含用户有权访问的资源列表和权限详情。
     * 用于资源权限视图场景。
     * </p>
     *
     * @param r              权限查询结果
     * @param cacheTtlSeconds 缓存有效期（秒）
     * @return 资源查询响应
     */
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
                null,  // resourceTypeCode 由调用方填充
                res.getCode(), res.getCodeType(), res.getName(),
                perms.stream().anyMatch(e -> Boolean.TRUE.equals(e.canGrant())),
                ops, roleIds, permIds, sources));
        }
        return new QueryResourcesResp(entries, null, cacheTtlSeconds);
    }
}