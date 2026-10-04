package cn.ac.fage.accessmesh.access.sync;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 同步请求 sourceService 与可信服务身份一致性校验（T-ACCESS-004 修复 G2）。
 * <p>
 * 服务身份由认证入口从服务凭证行派生并绑定到
 * {@link AccessRequestContext#getServiceCode()}，不采信裸请求头中的服务编码。
 * 同步载荷必须匹配该可信身份，防止冒充其他服务。
 * </p>
 */
public final class SyncAuthVerifier {

    private SyncAuthVerifier() {
    }

    /**
     * 校验 payload sourceService 与已验证服务身份是否一致。
     *
     * @param payloadSourceService payload.sourceService（必填）
     * @param request              保留参数以兼容既有调用方；校验值取自可信上下文
     * @return true=一致，false=不一致或服务身份缺失
     */
    public static boolean verify(String payloadSourceService, HttpServletRequest request) {
        if (payloadSourceService == null || payloadSourceService.isBlank()) {
            return false;
        }
        // 已验证服务身份（凭证通过后由统一安全入口绑定；非 SERVICE 调用为 null）
        String verifiedServiceCode = AccessRequestContext.getServiceCode();
        if (verifiedServiceCode == null || verifiedServiceCode.isBlank()) {
            return false;
        }
        return payloadSourceService.equals(verifiedServiceCode);
    }
}
