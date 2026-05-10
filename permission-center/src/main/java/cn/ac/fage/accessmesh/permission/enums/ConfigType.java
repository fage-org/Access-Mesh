package cn.ac.fage.accessmesh.permission.enums;

/**
 * 配置类型枚举
 * <p>
 * 定义域配置的类型分类。
 * 配置类型用于区分不同用途的配置项。
 * </p>
 */
public enum ConfigType {
    /**
     * 范围配置
     * <p>
     * 定义权限查询的范围限制配置。
     * 例如：限制用户只能查看特定组织的数据。
     * </p>
     */
    SCOPE("SCOPE"),

    /**
     * 关系配置
     * <p>
     * 定义实体之间的关系配置。
     * 例如：角色与组织的绑定关系。
     * </p>
     */
    RELATION("RELATION"),

    /**
     * 绑定配置
     * <p>
     * 定义权限绑定的配置。
     * 例如：自动绑定默认角色的规则。
     * </p>
     */
    BINDING("BINDING"),

    /**
     * 子权限配置
     * <p>
     * 定义子权限的继承和派生配置。
     * 例如：子资源的权限继承规则。
     * </p>
     */
    SUB_PERM("SUB_PERM");

    private final String value;

    /**
     * 构造函数
     *
     * @param value 类型编码，对应数据库存储值
     */
    ConfigType(String value) {
        this.value = value;
    }

    /**
     * 获取类型编码
     *
     * @return 类型编码
     */
    public String getValue() { return value; }

    /**
     * 根据类型编码解析枚举
     *
     * @param value 类型编码
     * @return 对应的配置类型枚举
     * @throws IllegalArgumentException 如果类型编码不存在
     */
    public static ConfigType fromValue(String value) {
        for (ConfigType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown ConfigType value: " + value);
    }
}