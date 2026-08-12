package cn.ac.fage.accessmesh.access.permission.constant;

/**
 * 权限模块常量类
 * <p>
 * 定义权限模块中使用的各类常量值，包括目标类型、维护来源、编码类型等。
 * 这些常量用于实体属性值的标准化和一致性。
 * </p>
 */
public final class PermConstants {

    /**
     * 私有构造函数
     * <p>
     * 常量类不允许实例化。
     * </p>
     */
    private PermConstants() {}

    /**
     * 目标类型常量接口
     * <p>
     * 定义UserRole表的targetType字段使用的常量值。
     * 用于标识角色分配的目标类型。
     * </p>
     */
    public interface TargetType {
        /**
         * 分组角色类型
         * <p>
         * 表示角色分配的目标是分组角色。
         * </p>
         */
        String GROUP_ROLE = "GROUP_ROLE";

        /**
         * 基础角色类型
         * <p>
         * 表示角色分配的目标是基础角色。
         * </p>
         */
        String ROLE = "ROLE";

        /**
         * 用户类型
         * <p>
         * 表示角色分配的目标是用户。
         * </p>
         */
        String USER = "USER";
    }

    /**
     * 维护来源常量接口
     * <p>
     * 定义ResourceEntity表的maintainSource字段使用的常量值。
     * 用于标识资源的维护方式和来源。
     * </p>
     */
    public interface MaintainSource {
        /**
         * 服务同步来源
         * <p>
         * 表示资源由服务自动同步创建和维护。
         * 删除服务配置时会级联删除相关资源。
         * </p>
         */
        String SERVICE_SYNC = "SERVICE_SYNC";

        /**
         * 手动维护来源
         * <p>
         * 表示资源由管理员手动创建和维护。
         * 不会随服务配置删除而级联删除。
         * </p>
         */
        String MANUAL = "MANUAL";
    }

    /**
     * 编码类型常量接口
     * <p>
     * 定义ResourceEntity表的codeType字段使用的常量值。
     * 用于标识资源编码的生成方式。
     * </p>
     */
    public interface CodeType {
        /**
         * 默认编码类型
         * <p>
         * 表示资源使用系统默认编码。
         * 通常由服务同步自动生成。
         * </p>
         */
        String DEFAULT = "default";
    }

    /**
     * 条件逻辑常量接口
     * <p>
     * 定义权限条件评估使用的逻辑运算符。
     * 用于组合多个条件项的评估结果。
     * </p>
     */
    public interface ConditionLogic {
        /**
         * AND逻辑
         * <p>
         * 表示所有条件项都必须满足。
         * 用于严格的权限条件场景。
         * </p>
         */
        String AND = "AND";

        /**
         * OR逻辑
         * <p>
         * 表示任一条件项满足即可。
         * 用于宽松的权限条件场景。
         * </p>
         */
        String OR = "OR";
    }

    /**
     * 条件类型常量接口
     * <p>
     * 定义权限条件的类型分类。
     * 用于标识条件项的评估方式。
     * </p>
     */
    public interface ConditionType {
        /**
         * 日期范围条件
         * <p>
         * 表示权限在特定日期范围内生效。
         * </p>
         */
        String DATE_RANGE = "DATE_RANGE";

        /**
         * 时间范围条件
         * <p>
         * 表示权限在特定时间段内生效。
         * </p>
         */
        String TIME_RANGE = "TIME_RANGE";

        /**
         * IP白名单条件
         * <p>
         * 表示权限仅对白名单IP生效。
         * </p>
         */
        String IP_WHITELIST = "IP_WHITELIST";

        /**
         * IP黑名单条件
         * <p>
         * 表示权限对黑名单IP不生效。
         * </p>
         */
        String IP_BLACKLIST = "IP_BLACKLIST";
    }
}