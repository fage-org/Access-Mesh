package cn.ac.fage.accessmesh.permission.enums;

/**
 * 资源类型枚举
 * <p>
 * 定义资源实体的类型分类，用于区分不同类型的受保护对象。
 * 资源类型决定了资源在权限系统中的行为和展示方式。
 * </p>
 */
public enum ResourceType {
    /**
     * 菜单资源
     * <p>
     * 表示系统中的菜单项，如导航菜单、侧边栏菜单。
     * 菜单资源控制用户可见的页面入口。
     * 通常与前端路由关联。
     * </p>
     */
    MENU(1, "菜单"),

    /**
     * 按钮资源
     * <p>
     * 表示页面中的操作按钮，如添加、编辑、删除按钮。
     * 按钮资源控制用户可执行的操作入口。
     * 通常作为菜单资源的子资源。
     * </p>
     */
    BUTTON(2, "按钮"),

    /**
     * 接口资源
     * <p>
     * 表示后端API接口，如REST API、RPC接口。
     * 接口资源控制用户可调用的后端服务。
     * 通过Gateway进行接口级权限校验。
     * </p>
     */
    API(3, "接口"),

    /**
     * 数据资源
     * <p>
     * 表示数据级别的受保护对象，如数据表、数据行。
     * 数据资源控制用户可访问的数据范围。
     * 通常用于数据权限过滤。
     * </p>
     */
    DATA(4, "数据");

    private final int value;
    private final String label;

    /**
     * 构造函数
     *
     * @param value 类型值，对应数据库存储值
     * @param label 类型标签，用于显示
     */
    ResourceType(int value, String label) {
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
        for (ResourceType t : values()) {
            if (t.value == (value != null ? value : 0)) return t.label;
        }
        return "";
    }
}