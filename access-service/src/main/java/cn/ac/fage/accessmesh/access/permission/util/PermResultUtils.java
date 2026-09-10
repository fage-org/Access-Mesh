package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;

import java.util.*;

/**
 * 权限结果转换工具类
 * <p>
 * 提供PermResult转换为各种响应DTO的静态方法。
 * 在engine.query()返回结果后使用这些方法进行响应转换。
 * </p>
 * <p>
 * T-API-002（2026-09-06）check 族响应内部 id 字段族裁剪已被 T-API-003（2026-09-09）
 * 推翻：check 族三端点恢复结果记录全量回传（matchedRoleIds / matchedPermissionIds /
 * matchedResources[].resourceId），本工具类恢复产出该字段族；需要 matched id 集合
 * 的内部场景直接消费 {@link PermResult} 不经线格式中转（原消费方 explain 已随 T-PERM-059 删除，2026-09-10）。
 * 零调用的 toQueryResourcesResp 已删除（真实组装在
 * PermissionQueryAppServiceImpl.buildQueryResourcesResponse）。
 * </p>
 */
public final class PermResultUtils {

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private PermResultUtils() {}

    /**
     * 按 binaryBit 查找 OperationPermission
     *
     * @param opMap    操作权限映射（id → op）
     * @param binaryBit binaryBit 值
     * @return 匹配的 OperationPermission，未找到返回 null
     */
    private static OperationPermission findOpByBinaryBit(
            Map<Long, OperationPermission> opMap,
            Integer resourceType,
            Long binaryBit) {
        return OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, resourceType, binaryBit);
    }

    // ===== DTO转换 =====

    /**
     * 转换PermResult为AuthCheckResp
     * <p>
     * 将权限查询结果转换为权限校验响应DTO。
     * 包含校验结果、命中结果记录与条件评估状态
     * （T-API-003：matched id 字段族恢复回传，拒绝时为空列表）。
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
     * 转换PermResult为CheckInterfaceResp
     * <p>
     * 将权限查询结果转换为接口校验响应DTO。
     * 包含匹配的资源业务键信息与操作码。
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
                OperationPermission op = findOpByBinaryBit(opMap, perms.get(0).resourceType(), perms.get(0).grantedBits());
                String opCode = op != null ? op.getCode() : null;
                List<Long> roleIds = perms.stream().map(RolePermEntry::roleId).filter(Objects::nonNull).distinct().toList();
                List<Long> permIds = perms.stream().map(RolePermEntry::permissionId).filter(Objects::nonNull).distinct().toList();
                matched.add(new CheckInterfaceResp.MatchedResource(
                    res != null ? res.getId() : entry.getKey(),
                    null,  // resourceTypeCode 历史上未填充；消费方 Gateway 只读 allowed/reason/matchedResources.size()
                    res != null ? res.getCode() : null,
                    opCode, allowed, roleIds, permIds));
            }
        }
        return r.allowed()
            ? CheckInterfaceResp.allow(matched, cacheTtlSeconds)
            : CheckInterfaceResp.deny(r.reason() != null ? r.reason() : "DENIED", matched, cacheTtlSeconds);
    }
}
