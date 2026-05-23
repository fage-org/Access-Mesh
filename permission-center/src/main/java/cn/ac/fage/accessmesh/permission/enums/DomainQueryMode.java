package cn.ac.fage.accessmesh.permission.enums;

/**
 * 业务域查询模式枚举
 * <p>
 * 定义管理列表按业务域过滤时的三种查询策略：
 * <ul>
 *   <li>ALL — 不过滤，查看全部</li>
 *   <li>GLOBAL_PLUS — 查看全局域+指定业务域的内容</li>
 *   <li>DOMAIN_ONLY — 仅查看指定业务域的内容</li>
 * </ul>
 * </p>
 */
public enum DomainQueryMode {

    /**
     * 全部 — 不过滤，查看所有资源类型
     */
    ALL("ALL"),

    /**
     * 全局+指定域 — 查看指定域声明的资源类型 + 未被任何域认领的资源类型（全局域隐含内容）
     */
    GLOBAL_PLUS("GLOBAL_PLUS"),

    /**
     * 仅指定域 — 仅查看指定域声明的资源类型
     */
    DOMAIN_ONLY("DOMAIN_ONLY");

    private final String value;

    DomainQueryMode(String value) {
        this.value = value;
    }

    /**
     * 获取查询模式编码
     *
     * @return 查询模式编码
     */
    public String getValue() { return value; }
}
