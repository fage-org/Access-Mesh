package cn.ac.fage.accessmesh.permission.util;

/**
 * Operator context utilities for resolving operator ID.
 */
public final class OperatorUtil {

    private OperatorUtil() {
        // Utility class
    }

    /**
     * Resolve operator ID from context if not provided.
     *
     * @param operatorId Explicit operator ID, or null to auto-resolve from context
     * @return Resolved operator ID
     */
    public static Long resolveOrDefault(Long operatorId) {
        return operatorId != null ? operatorId : OperatorContext.getOperatorId();
    }
}