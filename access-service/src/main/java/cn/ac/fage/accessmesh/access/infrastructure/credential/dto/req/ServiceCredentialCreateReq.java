package cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 服务凭证签发请求（T-PERM-070）。
 * <p>secret 由服务端生成并在响应回显一次；同服务多凭证并存（轮换窗口）无数量上限。</p>
 *
 * @param serviceCode 绑定服务（须为 service_config 已注册且启用的服务编码）
 * @param expiresAt   过期时间（null=永不过期；须为未来时间）
 */
public record ServiceCredentialCreateReq(
    @NotBlank(message = "serviceCode 不能为空")
    @Size(max = 128, message = "serviceCode 长度不能超过 128")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "serviceCode 仅允许字母数字与 . _ - 且以字母数字开头")
    String serviceCode,

    @Future(message = "expiresAt 必须为未来时间（签发即过期的凭证无意义）")
    LocalDateTime expiresAt) {
}
