package cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp;

import cn.ac.fage.accessmesh.access.infrastructure.credential.entity.ServiceCredential;

import java.time.LocalDateTime;

/**
 * 服务凭证响应（T-PERM-070）。
 * <p>不含 secretHash（哈希无回查面——明文仅签发响应回显一次）。</p>
 *
 * @param id           主键
 * @param credentialId 线上凭证标识（sc- 前缀）
 * @param serviceCode  绑定服务编码
 * @param status       状态：0=停用 1=启用
 * @param rotatedAt    最近一次停用时刻（轮换/吊销同记；再启用不清空——保留停用历史供运维追溯）
 * @param expiresAt    过期时间（null=永不过期）
 * @param createdAt    签发时间
 */
public record ServiceCredentialResp(
    Long id,
    String credentialId,
    String serviceCode,
    Integer status,
    LocalDateTime rotatedAt,
    LocalDateTime expiresAt,
    LocalDateTime createdAt) {

    public static ServiceCredentialResp from(ServiceCredential entity) {
        return new ServiceCredentialResp(entity.getId(), entity.getCredentialId(), entity.getServiceCode(),
            entity.getStatus(), entity.getRotatedAt(), entity.getExpiresAt(), entity.getCreatedAt());
    }
}
