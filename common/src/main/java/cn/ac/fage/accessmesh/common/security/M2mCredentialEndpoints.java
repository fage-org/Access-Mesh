package cn.ac.fage.accessmesh.common.security;

import java.util.List;

/**
 * M2M 凭证端点白名单（T-PERM-070，单源，service-authentication.md §3.2/§3.3）。
 * <p>
 * 认证方式 {@code authMethod=CREDENTIAL} 的请求允许到达的精确端点清单——
 * <b>Gateway M2M 放行链（skipAuth 识别）与服务端仲裁器白名单强制消费同一份</b>，
 * 防两处漂移；SDK 直连同样受服务端白名单约束（不依赖 Gateway，直连调清单外
 * 端点一律 403，防凭证能力半径扩大到管理/查询端点）。
 * </p>
 * <p>
 * 清单为不可变常量：新增端点（阶段二逐端点扩展至全部 sync 族，见
 * service-authentication.md §3.5）须同步修改本类并回归两端测试。
 * {@code permission-manifest/full-sync} 为 T-PERM-071 依赖声明通道端点，
 * 随本卡预先登记（端点未上线前白名单命中无害——404 由路由层兜底）。
 * </p>
 */
public final class M2mCredentialEndpoints {

    private M2mCredentialEndpoints() {}

    /** M2M 凭证端点条目（method + 精确路径，无通配）。 */
    public record M2mEndpoint(String method, String path) {}

    /** 阶段一端点集（service-authentication.md §3.5）。 */
    private static final List<M2mEndpoint> ENDPOINTS = List.of(
        new M2mEndpoint("POST", "/api/access/resource-entity/sync"),
        new M2mEndpoint("POST", "/api/access/resource-entity/full-sync"),
        new M2mEndpoint("POST", "/api/access/integration/permission-manifest/full-sync"));

    /** 不可变端点清单（消费方遍历/测试断言用）。 */
    public static List<M2mEndpoint> endpoints() {
        return ENDPOINTS;
    }

    /**
     * 精确匹配（method 大小写不敏感、路径全等——无通配，防清单语义被前缀绕过）。
     */
    public static boolean matches(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        for (M2mEndpoint endpoint : ENDPOINTS) {
            if (endpoint.method().equalsIgnoreCase(method) && endpoint.path().equals(path)) {
                return true;
            }
        }
        return false;
    }
}
