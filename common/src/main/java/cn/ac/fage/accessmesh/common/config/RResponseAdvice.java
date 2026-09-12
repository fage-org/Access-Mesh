package cn.ac.fage.accessmesh.common.config;

import cn.ac.fage.accessmesh.common.model.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.UUID;

/**
 * R响应体增强
 * <p>
 * 自动填充requestId和traceId字段。T-PERM-021 F1.d 单 ID 收敛（2026-09-12）：
 * traceId 仅为 requestId 的响应字段别名，无第二套追踪体系——原 X-Trace-Id 头
 * 偏好路径删除（全仓无该头生产方，行为上恒等于 requestId）。
 * requestId 取值链：X-Request-Id 请求头（Gateway 生成/透传）→ 日志 MDC traceId
 * （access-service 内与上下文/审计 request_id 同源）→ 兜底 UUID。
 * </p>
 */
@ControllerAdvice
public class RResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    /** access-service RequestContextInterceptor 的 MDC 键（值 = 上下文第六要素 requestId）。 */
    private static final String MDC_TRACE_ID = "traceId";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return R.class.equals(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof R<?> result) {
            String requestId = resolveRequestId();
            result.setRequestId(requestId);
            result.setTraceId(requestId);
        }
        return body;
    }

    private String resolveRequestId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            String fromHeader = req.getHeader(REQUEST_ID_HEADER);
            if (fromHeader != null && !fromHeader.isBlank()) {
                // 与 access-service 拦截器同款规整（trim + 64 截断，T-PERM-021 F1.d 外评处置）：
                // 直连非规范头时响应壳与 MDC/审计 request_id 保持同一字符串
                return truncate(fromHeader.trim());
            }
        }
        String fromMdc = MDC.get(MDC_TRACE_ID);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return UUID.randomUUID().toString();
    }

    /** 对齐审计 request_id 列宽与拦截器截断口径。 */
    private static String truncate(String value) {
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
