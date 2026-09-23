package cn.ac.fage.accessmesh.access.engine;

/**
 * Admin模块权限验证器接口
 * <p>
 * 提供Admin模块资源的权限校验方法。
 * </p>
 *
 * <p>使用示例：
 * <pre>
 * // 类型级校验（CREATE操作）
 * validator.checkTypeLevel(ResourceTypeCode.USER, OperationCode.CREATE);
 *
 * // 实例级校验（UPDATE/DELETE操作）
 * validator.checkInstanceLevel(ResourceTypeCode.USER, "123", OperationCode.UPDATE);
 *
 * // 批量实例级校验
 * validator.checkBatchInstanceLevel(ResourceTypeCode.USER, List.of("123", "456"), OperationCode.DELETE);
 * </pre>
 * </p>
 *
 * <p>自我修改豁免应在业务层调用验证器之前处理（豁免范围 T-PERM-067 收窄：仅档案字段与自助改密通道，启停/删除不豁免）。
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
     * @param operationCode    操作码（如 OperationCode.CREATE）
     */
    void checkTypeLevel(String resourceTypeCode, String operationCode);

    /**
     * 类型级权限非抛出判定（T-ADMIN-021，供「部分裁剪」类调用方使用）。
     * <p>
     * 仅在权限引擎<b>成功响应</b>且判定拒绝时返回 false；引擎技术故障（如数据库异常）抛
     * {@link cn.ac.fage.accessmesh.common.exception.SystemException} 向上（fail-closed，不得
     * 静默降级为裁剪结果——P2-1）；操作者主体缺失抛 {@link SecurityException}（403 明确拒绝，
     * 与 checkAndThrow 同一定性——T-ACCESS-052 修订：sys_user 存在而权限投影缺失是可感知的
     * 拒绝非技术故障）。
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
     * @param operationCode    操作码（如 OperationCode.UPDATE）
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

    /**
     * 批量实例级非抛出判定（T-ADMIN-025，供「按可见集过滤」类调用方使用）。
     * <p>
     * 返回输入编码中被权限引擎明确拒绝的子集（含无投影实体 fail-closed 拒绝的编码），
     * 引擎语义与 {@link #checkBatchInstanceLevel} 完全一致，仅不抛安全拒绝——
     * 调用方据此裁剪可见范围（如文件 page 按可见文件夹过滤）。scopeAll 命中返回空集。
     * 操作者主体缺失抛 {@link SecurityException}（与抛出型同语义，会话身份问题非技术故障）；
     * 引擎技术故障向上传播。
     * </p>
     *
     * @param resourceTypeCode 资源类型码
     * @param resourceCodes    资源实例码集合
     * @param operationCode    操作码
     * @return 被拒绝的业务编码集合（空集=全部允许）
     */
    java.util.Set<String> getDeniedResourceCodes(String resourceTypeCode,
                                                 java.util.Set<String> resourceCodes,
                                                 String operationCode);
}