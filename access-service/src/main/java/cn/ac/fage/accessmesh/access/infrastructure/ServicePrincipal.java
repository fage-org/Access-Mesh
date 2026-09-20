package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 已验证的服务主体（T-PERM-070，service-authentication.md §3.2）。
 * <p>
 * 凭证认证成功后由 {@code ServiceAuthArbiter}（order=1）写入 request attribute
 * （{@link SecurityAttributes#ATTR_SERVICE_PRINCIPAL}）——服务端内存对象，
 * 调用方无法伪造（外部请求不能设置 servlet attribute）；后续拦截器与上下文绑定
 * 只消费本对象，不再读取 X-Service-Code / X-Tenant-Id 自报头。
 * </p>
 *
 * @param tenantId     已验证租户 ID（凭证行派生）
 * @param serviceCode  已验证服务编码（凭证行派生）
 * @param credentialId 凭证标识（审计/日志定位）
 */
public record ServicePrincipal(Long tenantId, String serviceCode, String credentialId) {
}
