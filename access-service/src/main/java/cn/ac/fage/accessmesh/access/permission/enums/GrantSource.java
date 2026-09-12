package cn.ac.fage.accessmesh.access.permission.enums;

import lombok.Getter;

/**
 * 权限授予来源枚举
 * <p>
 * 定义角色权限的授予来源类型。
 * 用于区分手动授权和自动授权的来源。
 * </p>
 */
@Getter
public enum GrantSource {
    /**
     * 手动授权
     * <p>
     * 表示权限由管理员手动授予。
     * 通过权限管理界面或API直接配置。
     * </p>
     */
    MANUAL("MANUAL"),

    /**
     * 依赖自动补全授权
     * <p>
     * 表示权限由 resource_dependency 规则自动补全授予。
     * grant_source=AUTO_DEP 时，grant_dep_id 记录触发的 resource_dependency.id。
     * </p>
     */
    AUTO_DEP("AUTO_DEP"),

    /**
     * 授权根种子（类型生命周期维护，T-PERM-062）
     * <p>
     * 表示权限行是自定义 resource_type 的首授基座：类型创建/追加操作/所有者变更时
     * 由类型生命周期写路径同事务落库（scopeAll + canGrant + 单操作位，DDL CHECK 焊死形状），
     * apply-grant-plan 不可改删（20061，对齐 AUTO_DEP 只读先例）；类型删除级联清理
     * （T-PERM-050）。写入通道为系统侧种子直写（跳过委托校验），非管理员豁免。
     * </p>
     */
    AUTHORITY_ROOT("AUTHORITY_ROOT");

    private final String value;

    /**
     * 构造函数
     *
     * @param value 来源编码，对应数据库存储值
     */
    GrantSource(String value) {
        this.value = value;
    }
}