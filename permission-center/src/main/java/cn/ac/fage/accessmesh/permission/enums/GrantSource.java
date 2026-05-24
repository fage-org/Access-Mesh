package cn.ac.fage.accessmesh.permission.enums;

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
    MANUAL("MANUAL");

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