package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;

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

    // ===== 批量过滤 =====

    /**
     * 按操作权限过滤角色权限条目
     * <p>
     * 过滤出授予操作权限覆盖目标操作权限的所有条目。
     * 用于权限判定时的批量筛选。
     * </p>
     *
     * @param entries   角色权限条目列表
     * @param opCache   操作权限缓存映射
     * @param targetOp  目标操作权限
     * @return 过滤后的角色权限条目列表
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
     * 按多个操作权限过滤并分组角色权限条目
     * <p>
     * 根据目标操作权限ID集合过滤条目，并按操作权限ID分组。
     * 用于批量权限判定场景。
     * </p>
     *
     * @param entries    角色权限条目列表
     * @param opCache    操作权限缓存映射
     * @param targetOpIds 目标操作权限ID集合
     * @return 按操作权限ID分组的过滤结果映射
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