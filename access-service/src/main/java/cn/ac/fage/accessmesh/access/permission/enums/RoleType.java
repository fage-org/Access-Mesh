package cn.ac.fage.accessmesh.access.permission.enums;

import lombok.Getter;

/**
 * 角色类型枚举
 * <p>
 * 定义抽象角色的类型分类，用于区分不同用途的角色实体。
 * 角色类型决定了角色的层级特性、权限配置能力等行为。
 * </p>
 */
@Getter
public enum RoleType {
    /**
     * 组织角色
     * <p>
     * 表示组织架构中的角色，如部门、分公司等。
     * 支持层级结构，删除时级联删除所有子孙角色。
     * 可以配置权限。
     * </p>
     */
    ORG(1, "组织"),

    /**
     * 职位角色
     * <p>
     * 表示组织中的职位角色，如经理、员工等。
     * 不支持层级结构，删除时不会级联删除。
     * 可以配置权限。
     * </p>
     */
    POSITION(2, "职位"),

    /**
     * 个人角色
     * <p>
     * 表示个人专属的角色，如临时授权角色。
     * 不支持层级结构，删除时不会级联删除。
     * 可以配置权限。
     * </p>
     */
    PERSONAL(3, "个人"),

    /**
     * 分组角色
     * <p>
     * 表示角色分组容器，用于组织角色层级。
     * 支持层级结构，删除时级联删除所有子孙角色。
     * 不能配置权限，仅作为角色分组容器。
     * </p>
     */
    GROUP_ROLE(5, "分组角色"),

    /**
     * 基本角色
     * <p>
     * 表示基础业务角色，如管理员、普通用户等。
     * 不支持层级结构，删除时不会级联删除。
     * 可以配置权限。
     * </p>
     */
    BASIC_ROLE(6, "基本角色");

    private final int value;
    private final String label;

    /**
     * 构造函数
     *
     * @param value 类型值，对应数据库存储值
     * @param label 类型标签，用于显示
     */
    RoleType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 根据类型值解析枚举
     *
     * @param value 类型值
     * @return 对应的角色类型枚举
     * @throws IllegalArgumentException 如果类型值不存在
     */
    public static RoleType fromValue(int value) {
        for (RoleType t : values()) {
            if (t.value == value) return t;
        }
        throw new IllegalArgumentException("Unknown RoleType value: " + value);
    }

    /**
     * 安全获取类型标签
     * <p>
     * 根据类型值获取标签，如果类型值无效返回空字符串。
     * 用于显示场景，避免异常。
     * </p>
     *
     * @param value 类型值，可以为null
     * @return 类型标签，无效类型返回空字符串
     */
    public static String safeGetLabel(Integer value) {
        try {
            return fromValue(value != null ? value : 0).getLabel();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}