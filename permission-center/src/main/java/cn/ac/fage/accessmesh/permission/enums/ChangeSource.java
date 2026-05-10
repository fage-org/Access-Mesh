package cn.ac.fage.accessmesh.permission.enums;

/**
 * 权限变更来源枚举
 * <p>
 * 定义权限变更记录的来源类型。
 * 用于追踪权限变更的触发方，便于审计和溯源。
 * </p>
 */
public enum ChangeSource {
    /**
     * 管理员操作
     * <p>
     * 表示权限变更由管理员通过管理界面执行。
     * 包括角色授权、权限分配、用户管理等操作。
     * </p>
     */
    ADMIN("ADMIN"),

    /**
     * 同步操作
     * <p>
     * 表示权限变更由外部系统同步触发。
     * 包括用户同步、角色同步、组织架构同步等。
     * </p>
     */
    SYNC("SYNC"),

    /**
     * API调用
     * <p>
     * 表示权限变更通过API接口调用执行。
     * 用于第三方系统集成的权限管理场景。
     * </p>
     */
    API("API"),

    /**
     * 系统自动
     * <p>
     * 表示权限变更由系统自动触发。
     * 包括权限过期清理、角色到期失效、缓存失效等。
     * </p>
     */
    SYSTEM("SYSTEM");

    private final String value;

    /**
     * 构造函数
     *
     * @param value 来源编码，对应数据库存储值
     */
    ChangeSource(String value) {
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
     * @return 对应的变更来源枚举
     * @throws IllegalArgumentException 如果来源编码不存在
     */
    public static ChangeSource fromValue(String value) {
        for (ChangeSource t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown ChangeSource value: " + value);
    }
}