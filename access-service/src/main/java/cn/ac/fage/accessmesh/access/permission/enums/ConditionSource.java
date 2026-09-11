package cn.ac.fage.accessmesh.access.permission.enums;

import lombok.Getter;

/**
 * 权限条件来源枚举（T-PERM-048 双轨制定案 2026-09-11）
 * <p>
 * 区分两类管理边界互斥的条件：
 * <ul>
 *   <li>{@link #MANAGED}：权限条件页面管理的条件——只能在权限条件页 CRUD，
 *       授权页只能引用（conditionCode）；有 resource_entity(CONDITION) 实例投影
 *       （code=条件 code），可被实例级授权 CONDITION:UPDATE/DELETE@code。</li>
 *   <li>{@link #INLINE}：授权页内联条件——随 apply-grant-plan 同事务创建/回收
 *       （1:1 属于授权记录、code 自动生成 inline- 前缀、enabled 恒 true），
 *       权限条件页查不到也不能管理（list 默认/管理面写入口 20060 拒绝），
 *       不建投影行（无资源身份消费者，授权树零过滤）。</li>
 * </ul>
 * </p>
 */
@Getter
public enum ConditionSource {
    /**
     * 管理页条件（权限条件页 CRUD；有实例投影）
     */
    MANAGED("MANAGED"),

    /**
     * 授权页内联条件（apply-grant-plan 同事务生命周期；无投影）
     */
    INLINE("INLINE");

    private final String value;

    /**
     * 构造函数
     *
     * @param value 来源编码，对应 permission_condition.source 存储值
     */
    ConditionSource(String value) {
        this.value = value;
    }
}
