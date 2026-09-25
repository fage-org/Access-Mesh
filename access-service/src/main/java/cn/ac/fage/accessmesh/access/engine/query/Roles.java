package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Set;

/**
 * 指定角色视角主体（T-PERM-082，设计 §2.2）。
 * <p>
 * 可信内部用途（角色配置/诊断模拟）：角色集原样进入运行态，不暗中解析用户、不补加角色、
 * 不做 ROLE_MUTEX 过滤（R03 口径：Roles({R1,R2}) 互斥对事实完整返回）。
 * {@code Roles(empty)} 合法，运行时返回 NO_ROLE，不回退登录用户。
 * 集合为 null 或含 null 元素属编程错误（构造期 NPE）；空集合合法。
 * </p>
 *
 * @param roleIds 角色 id 集合，非 null（可为空集）；元素须为正数（结构校验）
 */
public record Roles(Set<Long> roleIds) implements Subject {

    public Roles {
        roleIds = Set.copyOf(roleIds);
    }
}
