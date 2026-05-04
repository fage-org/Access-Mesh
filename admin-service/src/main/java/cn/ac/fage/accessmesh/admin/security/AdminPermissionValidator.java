package cn.ac.fage.accessmesh.admin.security;

/**
 * Admin-service permission validator interface.
 * Provides permission check methods for admin module resources.
 *
 * <p>Usage:
 * <pre>
 * // Type-level check (CREATE operation)
 * validator.checkTypeLevel(AdminResourceType.USER, AdminOperationCode.CREATE);
 *
 * // Instance-level check (UPDATE/DELETE operation)
 * validator.checkInstanceLevel(AdminResourceType.USER, "123", AdminOperationCode.UPDATE);
 *
 * // Batch instance-level check
 * validator.checkBatchInstanceLevel(AdminResourceType.USER, List.of("123", "456"), AdminOperationCode.DELETE);
 * </pre>
 *
 * <p>Self-modification exemption should be handled in business layer before calling validator.
 */
public interface AdminPermissionValidator {

    /**
     * Type-level permission check (for CREATE operations).
     * Throws SecurityException if permission denied.
     *
     * @param resourceTypeCode resource type code (e.g., AdminResourceType.USER)
     * @param operationCode operation code (e.g., AdminOperationCode.CREATE)
     */
    void checkTypeLevel(String resourceTypeCode, String operationCode);

    /**
     * Instance-level permission check (for UPDATE/DELETE operations).
     * Throws SecurityException if permission denied.
     *
     * @param resourceTypeCode resource type code (e.g., AdminResourceType.USER)
     * @param resourceCode resource instance code (e.g., userId.toString())
     * @param operationCode operation code (e.g., AdminOperationCode.UPDATE)
     */
    void checkInstanceLevel(String resourceTypeCode, String resourceCode, String operationCode);

    /**
     * Batch instance-level permission check.
     * Throws SecurityException if any resource permission denied.
     *
     * @param resourceTypeCode resource type code
     * @param resourceCodes list of resource instance codes
     * @param operationCode operation code
     */
    void checkBatchInstanceLevel(String resourceTypeCode, java.util.List<String> resourceCodes, String operationCode);
}