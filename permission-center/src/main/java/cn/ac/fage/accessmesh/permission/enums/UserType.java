package cn.ac.fage.accessmesh.permission.enums;

/**
 * 用户类型枚举
 * <p>
 * 定义抽象用户的类型分类，用于区分不同来源的用户实体。
 * 用户类型来自 type_definition 表的预置值。
 * </p>
 */
public enum UserType {
    /**
     * 人员类型
     * <p>
     * 表示系统中的实际用户，如管理员、普通用户等。
     * 通常从外部用户系统同步创建。
     * </p>
     */
    USER(1, "人员"),

    /**
     * 服务类型
     * <p>
     * 表示服务账号，用于微服务之间的身份认证。
     * 服务账号拥有独立的权限配置。
     * </p>
     */
    SERVICE(2, "服务");

    private final int value;
    private final String label;

    /**
     * 构造函数
     *
     * @param value 类型值，对应数据库存储值
     * @param label 类型标签，用于显示
     */
    UserType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 获取类型值
     *
     * @return 类型值
     */
    public int getValue() { return value; }

    /**
     * 获取类型标签
     *
     * @return 类型标签
     */
    public String getLabel() { return label; }

    /**
     * 根据类型值解析枚举
     *
     * @param value 类型值
     * @return 对应的用户类型枚举
     * @throws IllegalArgumentException 如果类型值不存在
     */
    public static UserType fromValue(int value) {
        for (UserType t : values()) {
            if (t.value == value) return t;
        }
        throw new IllegalArgumentException("Unknown UserType value: " + value);
    }
}