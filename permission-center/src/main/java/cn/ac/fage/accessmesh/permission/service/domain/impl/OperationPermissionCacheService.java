package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作权限缓存服务
 * <p>
 * 按 tenantId + resourceType 分组缓存，每组最多63个操作。
 * 缓存时间：60分钟（操作定义通常不变）。
 * </p>
 * <p>
 * 核心方法：
 * - loadByResourceType: 预加载指定资源类型的所有操作权限
 * - computeCoveringBits: 计算覆盖目标操作的所有 binaryBit 值
 * - computeTargetBitMask: 计算目标位掩码（用于数据库位操作查询）
 * - findByBinaryBit: 按 binaryBit 反查 OperationPermission
 * - codeToBinaryBit: 操作码转 binaryBit
 * </p>
 */
@Service
public class OperationPermissionCacheService {

    private final OperationPermissionMapper operationPermissionMapper;
    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param operationPermissionMapper 操作权限数据访问层
     * @param cacheService              缓存服务
     */
    public OperationPermissionCacheService(
            OperationPermissionMapper operationPermissionMapper,
            CacheService cacheService) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.cacheService = cacheService;
    }

    /**
     * 按资源类型加载所有操作权限
     * <p>
     * 使用双层缓存（L1_L2），缓存 Key 格式："op_perm:" + resourceType。
     * 缓存时间：60分钟。
     * </p>
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @return 操作权限映射（id → OperationPermission）
     */
    public Map<Long, OperationPermission> loadByResourceType(Long tenantId, Integer resourceType) {
        String cacheKey = buildCacheKey(resourceType);
        Map<Long, OperationPermission> cached = cacheService.get(
            PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, cacheKey);
        if (cached != null) {
            return cached;
        }

        List<OperationPermission> ops = operationPermissionMapper.selectByTenantAndResourceType(
            tenantId, resourceType);
        Map<Long, OperationPermission> opMap = ops.stream()
            .collect(Collectors.toMap(OperationPermission::getId, op -> op, (a, b) -> a));

        if (!opMap.isEmpty()) {
            cacheService.put(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE,
                tenantId, cacheKey, opMap);
        }
        return opMap;
    }

    /**
     * 计算覆盖目标操作的所有 binaryBit 值
     * <p>
     * 从 inheritMask 推算：如果某操作的 effectiveBits 包含目标 binaryBit，
     * 则该操作的 binaryBit 应被包含在覆盖集中。
     * </p>
     * <p>
     * 例如：目标 VIEW(binaryBit=1)
     * - VIEW.effectiveBits = 1 → 覆盖集包含 VIEW.binaryBit = 1
     * - MANAGE.effectiveBits = 15 (8|7) → 包含 VIEW → 覆盖集包含 MANAGE.binaryBit = 8
     * </p>
     *
     * @param tenantId        租户ID
     * @param resourceType    资源类型值
     * @param targetBinaryBit 目标操作的 binaryBit
     * @return 覆盖目标操作的所有 binaryBit 值集合
     */
    public Set<Long> computeCoveringBits(Long tenantId, Integer resourceType, Long targetBinaryBit) {
        if (targetBinaryBit == null || targetBinaryBit == 0L) {
            return Collections.emptySet();
        }
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        Set<Long> coveringBits = new HashSet<>();
        for (OperationPermission op : opMap.values()) {
            long effectiveBits = op.getEffectiveBits();
            if ((effectiveBits & targetBinaryBit) != 0) {
                coveringBits.add(op.getBinaryBit());
            }
        }
        return coveringBits;
    }

    /**
     * 计算目标位掩码（用于数据库查询）
     * <p>
     * 将覆盖集中所有 binaryBit 进行 OR 运算，得到位掩码。
     * 用于 PostgreSQL 位操作查询：WHERE granted_bits & targetBitMask != 0
     * </p>
     *
     * @param tenantId        租户ID
     * @param resourceType    资源类型值
     * @param targetBinaryBit 目标操作的 binaryBit
     * @return 位掩码值
     */
    public long computeTargetBitMask(Long tenantId, Integer resourceType, Long targetBinaryBit) {
        Set<Long> coveringBits = computeCoveringBits(tenantId, resourceType, targetBinaryBit);
        long mask = 0L;
        for (Long bit : coveringBits) {
            mask |= bit;
        }
        return mask;
    }

    /**
     * 按 binaryBit 查找 OperationPermission
     * <p>
     * 从缓存中反查，用于填充 RolePermEntry.operationCode/effectiveBits。
     * </p>
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param binaryBit    操作的 binaryBit 值
     * @return 匹配的 OperationPermission，未找到返回 null
     */
    public OperationPermission findByBinaryBit(Long tenantId, Integer resourceType, Long binaryBit) {
        if (binaryBit == null) {
            return null;
        }
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        return opMap.values().stream()
            .filter(op -> Objects.equals(op.getBinaryBit(), binaryBit))
            .findFirst()
            .orElse(null);
    }

    /**
     * 批量按 binaryBit 查找 OperationPermission
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param binaryBits   binaryBit 值集合
     * @return binaryBit → OperationPermission 映射
     */
    public Map<Long, OperationPermission> findByBinaryBits(Long tenantId, Integer resourceType, Set<Long> binaryBits) {
        if (binaryBits == null || binaryBits.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        Map<Long, OperationPermission> result = new HashMap<>();
        for (Long bit : binaryBits) {
            OperationPermission op = opMap.values().stream()
                .filter(o -> Objects.equals(o.getBinaryBit(), bit))
                .findFirst()
                .orElse(null);
            if (op != null) {
                result.put(bit, op);
            }
        }
        return result;
    }

    /**
     * 操作码转 binaryBit
     * <p>
     * 从缓存中查找指定操作码对应的 binaryBit 值。
     * </p>
     *
     * @param tenantId      租户ID
     * @param resourceType  资源类型值
     * @param operationCode 操作码
     * @return binaryBit 值，未找到返回 null
     */
    public Long codeToBinaryBit(Long tenantId, Integer resourceType, String operationCode) {
        if (operationCode == null || operationCode.isEmpty()) {
            return null;
        }
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        return opMap.values().stream()
            .filter(op -> Objects.equals(op.getCode(), operationCode))
            .map(OperationPermission::getBinaryBit)
            .findFirst()
            .orElse(null);
    }

    /**
     * 批量操作码转 binaryBit
     *
     * @param tenantId       租户ID
     * @param resourceType   资源类型值
     * @param operationCodes 操作码集合
     * @return 操作码 → binaryBit 映射
     */
    public Map<String, Long> codesToBinaryBits(Long tenantId, Integer resourceType, Set<String> operationCodes) {
        if (operationCodes == null || operationCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, OperationPermission> opMap = loadByResourceType(tenantId, resourceType);
        Map<String, Long> result = new HashMap<>();
        for (OperationPermission op : opMap.values()) {
            if (operationCodes.contains(op.getCode())) {
                result.put(op.getCode(), op.getBinaryBit());
            }
        }
        return result;
    }

    /**
     * 失效指定资源类型的缓存
     * <p>
     * 当 OperationPermission 表有变更时调用。
     * </p>
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     */
    public void evict(Long tenantId, Integer resourceType) {
        String cacheKey = buildCacheKey(resourceType);
        cacheService.evict(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, cacheKey);
    }

    /**
     * 失效所有资源类型的缓存
     * <p>
     * 当 OperationPermission 表有批量变更时调用。
     * </p>
     *
     * @param tenantId 租户ID
     */
    public void evictAll(Long tenantId) {
        cacheService.evictAll(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId);
    }

    /**
     * 构建缓存 Key
     *
     * @param resourceType 资源类型值
     * @return 缓存 Key
     */
    private String buildCacheKey(Integer resourceType) {
        return "op_perm:" + resourceType;
    }
}