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
 * validator.checkTypeLevel(ResourceTypeCode.USER, AdminOperationCode.CREATE);
 *
 * // 实例级校验（UPDATE/DELETE操作）
 * validator.checkInstanceLevel(ResourceTypeCode.USER, "123", AdminOperationCode.UPDATE);
 *
 * // 批量实例级校验
 * validator.checkBatchInstanceLevel(ResourceTypeCode.USER, List.of("123", "456"), AdminOperationCode.DELETE);
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
     * @param resourceTypeCode 资源类型码（如 ResourceTypeCode.USER）
     * @param operationCode    操作码（如 AdminOperationCode.CREATE）
     */
    void checkTypeLevel(String resourceTypeCode, String operationCode);

    /**
     * 类型级权限非抛出判定（T-ADMIN-021，供「部分裁剪」类调用方使用）。
     * <p>
     * 仅在权限引擎<b>成功响应</b>且判定拒绝时返回 false；本地权限引擎技术故障
     * （如数据库异常）或操作者主体缺失时抛 {@link cn.ac.fage.accessmesh.common.exception.SystemException}
     * 向上（fail-closed，不得静默降级为裁剪结果——P2-1）。不复用 SecurityException
     * （全局映射 403，与故障语义矛盾）。
     * </p>
     *
     * @param resourceTypeCode 资源类型码
     * @param operationCode    操作码
     * @return true=允许；false=引擎成功响应且明确拒绝
     */
    boolean hasTypeLevel(String resourceTypeCode, String operationCode);

    /**
     * 实例级权限校验（用于UPDATE/DELETE操作）
     * <p>
     * 权限拒绝时抛出SecurityException。
     * </p>
     *
     * @param resourceTypeCode 资源类型码（如 ResourceTypeCode.USER）
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