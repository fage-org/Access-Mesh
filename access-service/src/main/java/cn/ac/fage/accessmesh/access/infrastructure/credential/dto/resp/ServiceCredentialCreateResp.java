package cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp;

import java.time.LocalDateTime;

/**
 * 服务凭证签发响应（T-PERM-070）。
 * <p>{@code secret} 为明文 secret 的<b>唯一一次回显</b>——服务端只存 BCrypt 哈希，
 * 任何后续通道（list/detail/update）均不可回查；接入方须立即保存到密钥管理系统。</p>
 *
 * @param id           主键
 * @param credentialId 线上凭证标识（sc- 前缀，请求头 X-Credential-Id 值）
 * @param secret       明文 secret（sk- 前缀，请求头 X-Credential-Secret 值；仅此一次）
 * @param serviceCode  绑定服务编码
 * @param status       初始状态恒 1（启用）
 * @param expiresAt    过期时间（null=永不过期）
 */
public record ServiceCredentialCreateResp(
    Long id,
    String credentialId,
    String secret,
    String serviceCode,
    Integer status,
    LocalDateTime expiresAt) {
}
