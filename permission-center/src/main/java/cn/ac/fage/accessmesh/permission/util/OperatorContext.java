package cn.ac.fage.accessmesh.permission.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility for extracting operator identity from HTTP request headers.
 * Priority: X-User-Id Header (injected by Gateway)
 */
public final class OperatorContext {

    private static final Logger log = LoggerFactory.getLogger(OperatorContext.class);
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_SIGNATURE = "X-User-Signature";  // FIX #3: Added for signature verification

    private OperatorContext() {}

    /**
     * Gets the current operator's user ID.
     * @return operator user ID
     * @throws SecurityException if operator identity cannot be determined
     */
    public static Long getOperatorId() {
        HttpServletRequest request = getRequest();

        // FIX #3: Verify signature header exists (signature validation must have passed)
        // If HeaderSignatureInterceptor validated successfully, this header must be present
        String signature = request.getHeader(HEADER_SIGNATURE);
        if (signature == null || signature.isBlank()) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "Request not signed by gateway - missing X-User-Signature header",
                null,
                null
            );
            throw new SecurityException("Request not signed by gateway - signature validation bypass attempt detected");
        }

        // X-User-Id Header (injected by Gateway)
        String userIdHeader = request.getHeader(HEADER_USER_ID);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return Long.parseLong(userIdHeader.trim());
            } catch (NumberFormatException e) {
                // Use structured logging to prevent log injection attacks
                SecurityLogUtil.logSecurityEvent(
                    SecurityEventType.SUSPICIOUS_INPUT,
                    request,
                    "Invalid X-User-Id header format",
                    userIdHeader,
                    null
                );
            }
        }

        throw new SecurityException("Cannot determine operator identity - X-User-Id header not found or invalid");
    }

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new SecurityException("No HTTP request context available");
        }
        return attrs.getRequest();
    }
}
