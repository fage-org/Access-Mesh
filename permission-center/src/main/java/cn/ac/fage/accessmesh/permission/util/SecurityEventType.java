package cn.ac.fage.accessmesh.permission.util;

/**
 * Security event types for structured logging.
 * Used by SecurityLogUtil to categorize security-related events.
 */
public enum SecurityEventType {

    /**
     * Request blocked due to missing or invalid internal API secret.
     */
    BLOCKED_REQUEST,

    /**
     * Access denied due to insufficient permissions.
     */
    PERMISSION_DENIED,

    /**
     * Signature validation failed for user identity headers.
     */
    INVALID_SIGNATURE,

    /**
     * Tenant mismatch detected.
     */
    TENANT_MISMATCH,

    /**
     * Unauthorized access attempt.
     */
    UNAUTHORIZED_ACCESS,

    /**
     * Suspicious input detected (potential injection attack).
     */
    SUSPICIOUS_INPUT,

    /**
     * Rate limit exceeded.
     */
    RATE_LIMIT_EXCEEDED
}
