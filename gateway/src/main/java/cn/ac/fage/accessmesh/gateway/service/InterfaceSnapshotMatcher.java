package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 接口快照本地匹配器（T-PERM-001）
 * <p>
 * 在 Gateway 本地内存中对 InterfaceSnapshot 的 allowedApis 做匹配，避免每请求打 RPC。
 * 匹配规则（决策2：支持 Ant 通配匹配）：
 * <ol>
 *   <li>任一条目 {@code scopeAll=true} 且 {@code serviceCode} 匹配 → 放行（覆盖该服务全部接口）</li>
 *   <li>{@code httpMethod} 相等（条目为 null 视为通配）且 {@code pathPattern} 按 Ant 风格匹配请求路径 → 放行</li>
 *   <li>否则拒绝</li>
 * </ol>
 * </p>
 * <p>
 * 注意：{@code hasCondition} 条目目前按放行处理——条件评估在 permission-center 快照构建时已完成，
 * 快照内只包含评估通过且互斥过滤后的条目。若后续需要 Gateway 侧二次条件评估，再扩展本匹配器。
 * </p>
 */
public class InterfaceSnapshotMatcher {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 判断请求接口是否在快照允许范围内。
     *
     * @param snapshot    接口快照（null 或 allowedApis 为空视为无权限）
     * @param serviceCode 路由服务编码
     * @param httpMethod  请求 HTTP 方法
     * @param path        请求路径
     * @return true 允许访问，false 拒绝
     */
    public static boolean matches(InterfaceSnapshotResp snapshot, String serviceCode, String httpMethod, String path) {
        if (snapshot == null || snapshot.allowedApis() == null || snapshot.allowedApis().isEmpty()) {
            return false;
        }
        List<ApiPermissionEntry> entries = snapshot.allowedApis();
        // 优先判定 scopeAll 覆盖
        for (ApiPermissionEntry entry : entries) {
            if (entry.scopeAll() && serviceCode.equals(entry.serviceCode())) {
                return true;
            }
        }
        // 精确/通配匹配
        for (ApiPermissionEntry entry : entries) {
            if (entry.scopeAll()) continue;
            if (!serviceCode.equals(entry.serviceCode())) continue;
            if (entry.httpMethod() != null && !entry.httpMethod().equalsIgnoreCase(httpMethod)) continue;
            if (entry.pathPattern() == null) continue;
            if (PATH_MATCHER.match(entry.pathPattern(), path)) {
                return true;
            }
        }
        return false;
    }
}
