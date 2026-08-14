package cn.ac.fage.accessmesh.access.permission.service.sync;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 同步请求 sourceService 与可信服务身份一致性校验（T-ACCESS-004 修复 G2）。
 * <p>
 * 服务身份来源改为可信请求上下文：X-Service-Code 头由统一安全入口
 * （RequestContextInterceptor）在内部凭证（X-Internal-Secret）验证通过后绑定为
 * {@link AccessRequestContext#getServiceCode()}——无凭证的外部调用无法建立 SERVICE
 * 上下文，从而无法伪造 sourceService 冒充服务身份。本类不再直接读取裸请求头。
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
