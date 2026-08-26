package cn.ac.fage.accessmesh.example.dto;

/**
 * 演示接口 hello 响应体
 * <p>
 * 回显 Gateway HeaderEnrichFilter 注入的身份请求头，证明请求经 Gateway 鉴权后真实到达业务服务。
 * </p>
 *
 * @param greeting 问候语
 * @param userId   Gateway 注入的用户 ID（X-User-Id）
 * @param tenantId Gateway 注入的租户 ID（X-Tenant-Id）
 */
public record DemoHelloResp(String greeting, String userId, String tenantId) {
}
