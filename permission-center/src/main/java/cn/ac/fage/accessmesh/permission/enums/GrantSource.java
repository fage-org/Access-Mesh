package cn.ac.fage.accessmesh.permission.enums;

/**
 * 权限授予来源枚举
 * <p>
 * 定义角色权限的授予来源类型。
 * 用于区分手动授权和自动授权的来源。
 * </p>
 */
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
     * 依赖自动授权
     * <p>
     * 表示权限由资源依赖关系自动授予。
     * 当授予某资源权限时，自动授予依赖资源的权限。
     * 例如：授予菜单权限时，自动授予其子按钮权限。
     * </p>
     */
    AUTO_DEP("AUTO_DEP");

    private final String value;

    /**
     * 构造函数
     *
     * @param value 来源编码，对应数据库存储值
     */
    GrantSource(String value) {
        this.value = value;
    }

    /**
     * 获取来源编码
     *
     * @return 来源编码
     */
    public String getValue() { return value; }

    /**
     * 根据来源编码解析枚举
     *
     * @param value 来源编码
     * @return 对应的授予来源枚举
     * @throws IllegalArgumentException 如果来源编码不存在
     */
    public static GrantSource fromValue(String value) {
        for (GrantSource t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown GrantSource value: " + value);
    }
}