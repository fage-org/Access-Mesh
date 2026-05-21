package cn.ac.fage.accessmesh.permquery.domain.model.valueobject;

/**
 * 位掩码值对象
 * <p>
 * 封装位操作逻辑，用于权限匹配计算。
 * </p>
 */
public record BitMask(long value) {

    /**
     * 判断是否匹配目标位
     */
    public boolean matches(BitMask target) {
        return (value & target.value) != 0;
    }

    /**
     * 判断是否匹配目标位值
     */
    public boolean matches(long targetValue) {
        return (value & targetValue) != 0;
    }

    /**
     * 合并位掩码（或操作）
     */
    public BitMask union(BitMask other) {
        return new BitMask(value | other.value);
    }

    /**
     * 交集位掩码（与操作）
     */
    public BitMask intersect(BitMask other) {
        return new BitMask(value & other.value);
    }

    /**
     * 零值位掩码
     */
    public static BitMask zero() {
        return new BitMask(0L);
    }

    /**
     * 合并多个位掩码
     */
    public static BitMask unionAll(BitMask... masks) {
        long result = 0L;
        for (BitMask mask : masks) {
            result |= mask.value;
        }
        return new BitMask(result);
    }

    /**
     * 判断是否为空（无任何位）
     */
    public boolean isEmpty() {
        return value == 0L;
    }

    /**
     * 获取原始值
     */
    public long raw() {
        return value;
    }
}