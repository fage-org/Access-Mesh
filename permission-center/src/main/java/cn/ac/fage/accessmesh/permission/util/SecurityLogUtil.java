package cn.ac.fage.accessmesh.permission.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for structured security logging.
 * 
 * Provides JSON-formatted logs that are:
 * - Safe from log injection attacks (automatic escaping via JSON serialization)
 * - Easy to parse by log analysis systems (SIEM, ELK, etc.)
 * - Consistent in format across all security events
 */
public final class SecurityLogUtil {

    private static final Logger log = LoggerFactory.getLogger(SecurityLogUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private SecurityLogUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Log a security event in structured JSON format.
     */
    public static void logSecurityEvent(SecurityEventType eventType,
                                        HttpServletRequest request,
                                        String details,
                                        String userId,
                                        String tenantId) {
        try {
            Map<String, Object> logData = new LinkedHashMap<>();
            logData.put("eventType", eventType.name());
            logData.put("timestamp", Instant.now().toString());
            logData.put("method", request.getMethod());
            logData.put("uri", sanitizeUri(request.getRequestURI()));
            logData.put("clientIp", getClientIp(request));

            if (userId != null && !userId.isBlank()) {
                logData.put("userId", sanitizeUserId(userId));
            }
            if (tenantId != null && !tenantId.isBlank()) {
                logData.put("tenantId", sanitizeTenantId(tenantId));
            }
            if (details != null && !details.isBlank()) {
                logData.put("details", sanitizeDetails(details));
            }

            log.warn(objectMapper.writeValueAsString(logData));
        } catch (JsonProcessingException e) {
            log.warn("Security event: {} | method={} | uri={} | ip={}",
                eventType.name(),
                request.getMethod(),
                sanitizeUri(request.getRequestURI()),
                getClientIp(request));
        }
    }

    private static String sanitizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userId.replaceAll("[^0-9]", "");
    }

    private static String sanitizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        // Remove control characters using Unicode escape
        String sanitized = tenantId.replaceAll("[\u0000-\u001F]", "");
        if (sanitized.length() > 64) {
            return sanitized.substring(0, 64);
        }
        return sanitized;
    }

    private static String sanitizeUri(String uri) {
        if (uri == null) {
            return null;
        }
        // Remove control characters using Unicode escape
        return uri.replaceAll("[\u0000-\u001F]", "");
    }

    private static String sanitizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        // Remove control characters using Unicode escape
        String sanitized = details.replaceAll("[\u0000-\u001F]", "");
        if (sanitized.length() > 256) {
            return sanitized.substring(0, 253) + "...";
        }
        return sanitized;
    }

    private static String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (StringUtils.isNotEmpty(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader(HEADER_X_REAL_IP);
        if (StringUtils.isNotEmpty(realIp)) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
