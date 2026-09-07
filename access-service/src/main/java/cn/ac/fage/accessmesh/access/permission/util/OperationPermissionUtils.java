package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;

import java.util.*;

/**
 * 操作权限位运算工具类
 * <p>
 * 提供操作权限位掩码匹配和批量过滤的静态工具方法。
 * 用于权限判定时的位运算操作，提高权限匹配效率。
 * </p>
 */
public final class OperationPermissionUtils {

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private OperationPermissionUtils() {}

    // ===== 有效位掩码计算 =====

    /**
     * 计算操作权限的有效位掩码
     * <p>
     * 有效位掩码 = binaryBit | inheritMask。
     * 用于权限匹配时的位运算。
     * </p>
     *
     * @param op 操作权限实体
     * @return 有效位掩码值
     */
    public static long effectiveBits(OperationPermission op) {
        return effectiveBits(op.getBinaryBit(), op.getInheritMask());
    }

    /**
     * 计算有效位掩码
     * <p>
     * 有效位掩码 = binaryBit | inheritMask。
     * 用于权限匹配时的位运算。
     * </p>
     *
     * @param binaryBit   二进制位值
     * @param inheritMask 继承掩码值
     * @return 有效位掩码值
     */
    public static long effectiveBits(Long binaryBit, Long inheritMask) {
        long b = binaryBit == null ? 0L : binaryBit;
        long i = inheritMask == null ? 0L : inheritMask;
        return b | i;
    }

    // ===== 权限覆盖判定 =====

    /**
     * 判断授予的操作权限是否覆盖目标操作权限
     * <p>
     * 使用位运算判断 granted 的有效位掩码是否包含 target 的二进制位。
     * </p>
     *
     * @param granted 授予的操作权限
     * @param target  目标操作权限
     * @return 授予权限覆盖目标权限返回true，否则返回false
     */
    public static boolean covers(OperationPermission granted, OperationPermission target) {
        if (granted == null || target == null) return false;
        Long targetBit = target.getBinaryBit();
        if (targetBit == null || targetBit == 0L) return false;
        return (effectiveBits(granted) & targetBit) != 0L;
    }

    /**
     * 计算覆盖目标位的查询掩码
     * <p>
     * 返回所有能覆盖目标位的操作 binaryBit 按位 OR 后的结果，
     * 便于 PostgreSQL 使用 {@code granted_bits & bitMask != 0} 查询。
     * </p>
     *
     * @param operations      指定资源类型下的操作权限列表
     * @param targetBinaryBit 目标操作位
     * @return 查询掩码
     */
    public static long computeCoveringBitMask(Collection<OperationPermission> operations, Long targetBinaryBit) {
        if (operations == null || operations.isEmpty() || targetBinaryBit == null || targetBinaryBit == 0L) {
            return 0L;
        }
        long mask = 0L;
        for (OperationPermission operation : operations) {
            if (operation == null || operation.getBinaryBit() == null) {
                continue;
            }
            if ((effectiveBits(operation) & targetBinaryBit) != 0L) {
                mask |= operation.getBinaryBit();
            }
        }
        return mask;
    }

    /**
     * 展开授予操作实际覆盖的目标操作集合。
     * <p>
     * 与 {@link #computeCoveringBitMask(Collection, Long)} 的方向相反：
     * 前者用于「给定目标操作，找可命中的授权位」，本方法用于「给定授权操作，
     * 枚举它的有效位掩码覆盖了哪些操作」。调用方可用它构建最终可用操作投影。
     * </p>
     *
     * @param granted    显式授予的操作权限
     * @param candidates 同资源类型下的候选操作权限集合
     * @return granted 覆盖的操作权限列表，保持 candidates 的迭代顺序
     */
    public static List<OperationPermission> coveredOperations(
            OperationPermission granted,
            Collection<OperationPermission> candidates) {
        if (granted == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<OperationPermission> result = new ArrayList<>();
        for (OperationPermission candidate : candidates) {
            if (candidate == null || !Objects.equals(candidate.getResourceType(), granted.getResourceType())) {
                continue;
            }
            if (covers(granted, candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 按 resourceType + binaryBit 为操作权限建立索引
     *
     * @param operations 操作权限集合
     * @return 复合键到操作权限的映射
     */
    public static Map<String, OperationPermission> indexByResourceTypeAndBinaryBit(Collection<OperationPermission> operations) {
        if (operations == null || operations.isEmpty()) {
            return Map.of();
        }
        Map<String, OperationPermission> result = new LinkedHashMap<>();
        for (OperationPermission operation : operations) {
            if (operation == null || operation.getBinaryBit() == null) {
                continue;
            }
            result.put(composeKey(operation.getResourceType(), operation.getBinaryBit()), operation);
        }
        return result;
    }

    /**
     * 从复合索引中按 resourceType + binaryBit 查找操作权限
     *
     * @param indexedOperations 复合索引
     * @param resourceType      资源类型
     * @param binaryBit         操作位
     * @return 操作权限，未命中返回null
     */
    public static OperationPermission findIndexedByResourceTypeAndBinaryBit(
            Map<String, OperationPermission> indexedOperations,
            Integer resourceType,
            Long binaryBit) {
        if (indexedOperations == null || indexedOperations.isEmpty() || binaryBit == null) {
            return null;
        }
        return indexedOperations.get(composeKey(resourceType, binaryBit));
    }

    /**
     * 从 ID → OperationPermission 映射中按 resourceType + binaryBit 查找操作权限
     *
     * @param opCache      操作权限缓存
     * @param resourceType 资源类型
     * @param binaryBit    操作位
     * @return 操作权限，未命中返回null
     */
    public static OperationPermission findByResourceTypeAndBinaryBit(
            Map<Long, OperationPermission> opCache,
            Integer resourceType,
            Long binaryBit) {
        if (opCache == null || opCache.isEmpty()) {
            return null;
        }
        return findByResourceTypeAndBinaryBit(opCache.values(), resourceType, binaryBit);
    }

    /**
     * 从操作权限集合中按 resourceType + binaryBit 查找操作权限
     *
     * @param operations    操作权限集合
     * @param resourceType  资源类型
     * @param binaryBit     操作位
     * @return 操作权限，未命中返回null
     */
    public static OperationPermission findByResourceTypeAndBinaryBit(
            Collection<OperationPermission> operations,
            Integer resourceType,
            Long binaryBit) {
        if (operations == null || operations.isEmpty() || binaryBit == null) {
            return null;
        }
        for (OperationPermission operation : operations) {
            if (operation == null) {
                continue;
            }
            if (Objects.equals(operation.getResourceType(), resourceType)
                && Objects.equals(operation.getBinaryBit(), binaryBit)) {
                return operation;
            }
        }
        return null;
    }

    private static String composeKey(Integer resourceType, Long binaryBit) {
        return BusinessKeys.operationBitKey(resourceType, binaryBit);
    }
}
