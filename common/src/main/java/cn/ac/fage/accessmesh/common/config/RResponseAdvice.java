package cn.ac.fage.accessmesh.common.config;

import cn.ac.fage.accessmesh.common.model.R;
import jakarta.servlet.http.HttpServletRequest;
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
 * 自动填充requestId和traceId字段。
 * requestId从X-Request-Id请求头获取，若不存在则生成UUID。
 * traceId从X-Trace-Id请求头获取，若不存在则与requestId相同。
 * </p>
 */
@ControllerAdvice
public class RResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

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
            result.setTraceId(resolveTraceId(requestId));
        }
        return body;
    }

    private String resolveRequestId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            String fromHeader = req.getHeader(REQUEST_ID_HEADER);
            if (fromHeader != null && !fromHeader.isBlank()) {
                return fromHeader;
            }
        }
        return UUID.randomUUID().toString();
    }

    private String resolveTraceId(String fallback) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            String fromHeader = req.getHeader(TRACE_ID_HEADER);
            if (fromHeader != null && !fromHeader.isBlank()) {
                return fromHeader;
            }
        }
        return fallback;
    }
}
