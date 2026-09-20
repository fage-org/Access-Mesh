package cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 服务凭证更新请求（T-PERM-070，2026-09-20 拍板：update 支持 status + expiresAt）。
 * <p>三态语义：null=不修改。status 与 expiresAt 至少一个非 null（空 patch 拒绝 90001）；
 * 「清除过期时间」不提供（永不过期语义=签发新凭证时不设过期）。secret 与 credential_id
 * 不可改（轮换=签发新凭证并存，再停旧）。</p>
 *
 * @param id        凭证主键
 * @param status    目标状态：0=停用 1=启用（停用即轮换收尾/吊销，立即生效）
 * @param expiresAt 新过期时间（仅改期语义）
 */
public record ServiceCredentialUpdateReq(
    @NotNull(message = "id 不能为空")
    Long id,

    @Min(value = 0, message = "status 仅允许 0(停用)/1(启用)")
    @Max(value = 1, message = "status 仅允许 0(停用)/1(启用)")
    Integer status,

    @Future(message = "expiresAt 必须为未来时间（与 create 同口径——改到过去=立即过期死凭证）")
    LocalDateTime expiresAt) {
}
