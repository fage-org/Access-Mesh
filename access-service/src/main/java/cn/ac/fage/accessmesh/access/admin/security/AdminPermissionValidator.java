package cn.ac.fage.accessmesh.access.admin.security;

/**
 * Admin模块权限验证器接口
 * <p>
 * 提供Admin模块资源的权限校验方法。
 * </p>
 *
 * <p>使用示例：
 * <pre>
 * // 类型级校验（CREATE操作）
 * validator.checkTypeLevel(AdminResourceType.USER, AdminOperationCode.CREATE);
 *
 * // 实例级校验（UPDATE/DELETE操作）
 * validator.checkInstanceLevel(AdminResourceType.USER, "123", AdminOperationCode.UPDATE);
 *
 * // 批量实例级校验
 * validator.checkBatchInstanceLevel(AdminResourceType.USER, List.of("123", "456"), AdminOperationCode.DELETE);
 * </pre>
 * </p>
 *
 * <p>自我修改豁免应在业务层调用验证器之前处理。
 * </p>
 */
public interface AdminPermissionValidator {

    /**
     * 类型级权限校验（用于CREATE操作）
     * <p>
     * 权限拒绝时抛出SecurityException。
     * </p>
     *
     * @param resourceTypeCode 资源类型码（如 AdminResourceType.USER）
     * @param operationCode    操作码（如 AdminOperationCode.CREATE）
     */
    void checkTypeLevel(String resourceTypeCode, String operationCode);

    /**
     * 实例级权限校验（用于UPDATE/DELETE操作）
     * <p>
     * 权限拒绝时抛出SecurityException。
     * </p>
     *
     * @param resourceTypeCode 资源类型码（如 AdminResourceType.USER）
     * @param resourceCode     资源实例码（如 userId.toString()）
     * @param operationCode    操作码（如 AdminOperationCode.UPDATE）
     */
    void checkInstanceLevel(String resourceTypeCode, String resourceCode, String operationCode);

    /**
     * 批量实例级权限校验
     * <p>
     * 任一资源权限拒绝时抛出SecurityException。
     * </p>
     *
     * @param resourceTypeCode 资源类型码
     * @param resourceCodes    资源实例码列表
     * @param operationCode    操作码
     */
    void checkBatchInstanceLevel(String resourceTypeCode, java.util.List<String> resourceCodes, String operationCode);
}