package cn.ac.fage.accessmesh.permission.service.sync;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 同步请求 sourceService 与可信服务身份一致性校验。
 * <p>
 * 本阶段读取 X-Service-Code header 模拟可信身份，与 payload.sourceService 比较；
 * 不一致时返回 false，由调用方组装 SECURITY_DENIED 响应壳。
 * </p>
 */
public final class SyncAuthVerifier {

    /**
     * 可信服务身份请求头。
     */
    public static final String HEADER_SERVICE_CODE = "X-Service-Code";

    private SyncAuthVerifier() {
    }

    /**
     * 校验 payload sourceService 与请求头服务身份是否一致。
     *
     * @param payloadSourceService payload.sourceService（必填）
     * @param request              当前 HTTP 请求；可为 null（视为身份缺失）
     * @return true=一致，false=不一致或缺失
     */
    public static boolean verify(String payloadSourceService, HttpServletRequest request) {
        if (payloadSourceService == null || payloadSourceService.isBlank()) {
            return false;
        }
        if (request == null) {
            return false;
        }
        String header = request.getHeader(HEADER_SERVICE_CODE);
        if (header == null || header.isBlank()) {
            return false;
        }
        return payloadSourceService.equals(header);
    }
}
