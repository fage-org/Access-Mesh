package cn.ac.fage.accessmesh.common.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Gateway 内部互信专用审计载荷；租户来自已认证内部上下文，不接受载荷自报。 */
public record GatewayDenialAuditReq(
    @NotNull @Positive Long userId,
    @NotBlank @Size(max = 128) String serviceCode,
    @NotBlank @Size(max = 16) String httpMethod,
    @NotBlank @Size(max = 512) String path,
    @NotBlank @Size(max = 128) String reason,
    @Size(max = 64) String clientIp,
    @NotBlank @Size(max = 64) String requestId
) {}
