package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.access.engine.dto.PermResult;
import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.CheckInterfaceResp;
import cn.ac.fage.accessmesh.access.engine.query.DecisionResult;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;

import java.util.*;

/**
 * 权限结果转换工具类
 * <p>
 * 提供权限查询结果转换为各种响应DTO的静态方法。
 * toAuthCheckResp 已随 T-PERM-089 改为新 {@link DecisionResult} → 既有外部响应的
 * 纯转换（不重建旧 PermResult 再转换，设计 §9.1）；toCheckInterfaceResp 仍消费旧
 * PermResult（LEGACY_API checkInterface，T-PERM-090 迁移）。
 * </p>
 * <p>
 * T-API-002（2026-09-06）check 族响应内部 id 字段族裁剪已被 T-API-003（2026-09-09）
 * 推翻：check 族三端点恢复结果记录全量回传（matchedRoleIds / matchedPermissionIds /
 * matchedResources[].resourceId），本工具类恢复产出该字段族；需要 matched id 集合
 * 的内部场景直接消费引擎结果不经线格式中转。零调用的 toQueryResourcesResp 已删除
 * （真实组装在 PermissionQueryAppServiceImpl.buildQueryResourcesResponse）。
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
     * 转换新 DecisionResult 为 AuthCheckResp
     * <p>
     * 将新引擎单项最终判定转换为权限校验响应 DTO（纯转换，T-PERM-089）。
     * 拒绝原因词表 1:1（枚举 name 与旧 reason 字符串一致）；允许时命中 ID 与
     * 条件评估状态从结果详情派生（conditionEvaluated=保留事实中存在挂条件行，
     * 拒绝时为空列表/false）。
     * </p>
     *
     * @param r 新引擎单项最终判定结果
     * @return 权限校验响应
     */
    public static AuthCheckResp toAuthCheckResp(DecisionResult r) {
        if (r.outcome() == DecisionResult.Decision.DENY) {
            return AuthCheckResp.deny(r.reason() != null ? r.reason().name() : "DENIED");
        }
        boolean conditionEvaluated = r.details().stageFacts().stream()
            .flatMap(facts -> facts.retainedAfterEvaluation().stream())
            .anyMatch(GrantFact::hasCondition);
        return AuthCheckResp.allow(
            r.details().matchedRoleIds().stream().toList(),
            r.details().matchedPermissionIds().stream().toList(), conditionEvaluated);
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
