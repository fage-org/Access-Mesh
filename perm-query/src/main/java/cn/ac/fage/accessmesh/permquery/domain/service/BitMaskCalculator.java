package cn.ac.fage.accessmesh.permquery.domain.service;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.BitMask;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.EntityBatchLoadAdapter;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.PermCacheAdapter;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 位掩码计算领域服务
 * <p>
 * 负责计算权限查询所需的位掩码映射。
 * 使用缓存优化操作权限查找效率。
 * </p>
 */
@Service
public class BitMaskCalculator {

    private final PermCacheAdapter permCacheAdapter;
    private final EntityBatchLoadAdapter entityBatchLoadAdapter;
    private final OperationPermissionMapper operationPermissionMapper;

    public BitMaskCalculator(PermCacheAdapter permCacheAdapter,
                             EntityBatchLoadAdapter entityBatchLoadAdapter,
                             OperationPermissionMapper operationPermissionMapper) {
        this.permCacheAdapter = permCacheAdapter;
        this.entityBatchLoadAdapter = entityBatchLoadAdapter;
        this.operationPermissionMapper = operationPermissionMapper;
    }

    /**
     * 计算位掩码映射
     * <p>
     * 为每个资源类型计算位掩码，用于SQL位操作查询。
     * 位掩码包含所有能覆盖目标操作的操作位。
     * </p>
     *
     * @param tenantId      租户ID
     * @param resourceTypes 资源类型值集合
     * @param operationIds  操作权限ID集合
     * @return resourceType → bitMask 映射
     */
    public Map<Integer, Long> calculate(Long tenantId, Set<Integer> resourceTypes, Set<Long> operationIds) {
        if (tenantId == null || resourceTypes == null || resourceTypes.isEmpty()
            || operationIds == null || operationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 获取目标操作权限（按ID）
        Map<Long, OperationPermission> targetOps = entityBatchLoadAdapter.batchLoadOperations(tenantId, operationIds);
        if (targetOps.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, Long> result = new LinkedHashMap<>();
        for (Integer resourceType : resourceTypes) {
            // 获取该资源类型的所有操作权限（按ID索引，带缓存）
            Map<Long, OperationPermission> opMap = getOperationPermissionsByType(tenantId, resourceType);
            if (opMap.isEmpty()) {
                continue;
            }

            long mask = 0L;
            for (OperationPermission targetOp : targetOps.values()) {
                if (!Objects.equals(resourceType, targetOp.getResourceType())) {
                    continue;
                }
                // 计算覆盖目标操作的位掩码
                mask |= OperationPermissionUtils.computeCoveringBitMask(opMap.values(), targetOp.getBinaryBit());
            }
            if (mask != 0L) {
                result.put(resourceType, mask);
            }
        }
        return result;
    }

    /**
     * 计算单个资源类型的位掩码
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param operationIds 操作权限ID集合
     * @return 位掩码值
     */
    public BitMask calculateForType(Long tenantId, Integer resourceType, Set<Long> operationIds) {
        if (tenantId == null || resourceType == null || operationIds == null || operationIds.isEmpty()) {
            return BitMask.zero();
        }

        Map<Integer, Long> masks = calculate(tenantId, Set.of(resourceType), operationIds);
        Long mask = masks.get(resourceType);
        return mask != null ? new BitMask(mask) : BitMask.zero();
    }

    /**
     * 获取操作权限缓存（按资源类型）
     * <p>
     * 使用缓存优化，避免重复查询数据库。
     * </p>
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @return 操作权限映射（id → OperationPermission）
     */
    public Map<Long, OperationPermission> getOperationPermissionsByType(Long tenantId, Integer resourceType) {
        if (tenantId == null || resourceType == null) {
            return Collections.emptyMap();
        }

        // 尝试从缓存获取
        Map<Long, OperationPermission> cached = permCacheAdapter.getOperationPermissionsByType(tenantId, resourceType);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 从数据库加载
        List<OperationPermission> ops = operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType);
        if (ops.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, OperationPermission> opMap = ops.stream()
            .collect(Collectors.toMap(OperationPermission::getId, op -> op, (a, b) -> a));

        // 写入缓存
        permCacheAdapter.putOperationPermissionsByType(tenantId, resourceType, opMap);

        return opMap;
    }

    /**
     * 计算操作权限的有效位掩码
     *
     * @param op 操作权限实体
     * @return 有效位掩码值
     */
    public long effectiveBits(OperationPermission op) {
        return OperationPermissionUtils.effectiveBits(op);
    }

    /**
     * 判断授予权限是否覆盖目标操作
     *
     * @param granted 授予的操作权限
     * @param target  目标操作权限
     * @return 是否覆盖
     */
    public boolean covers(OperationPermission granted, OperationPermission target) {
        return OperationPermissionUtils.covers(granted, target);
    }

    /**
     * 失效操作权限缓存
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     */
    public void invalidateCache(Long tenantId, Integer resourceType) {
        if (tenantId != null && resourceType != null) {
            permCacheAdapter.evictOperationPermissionsByType(tenantId, resourceType);
        }
    }
}