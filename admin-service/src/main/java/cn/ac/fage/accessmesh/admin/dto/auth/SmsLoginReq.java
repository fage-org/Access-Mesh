package cn.ac.fage.accessmesh.admin.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record SmsLoginReq(
    @NotBlank(message = "租户ID不能为空")
    String tenantId,
    @NotBlank(message = "手机号不能为空")
    String phone,
    @NotBlank(message = "验证码不能为空")
    String smsCode,
    @NotBlank(message = "客户端ID不能为空")
    String clientId
) {}
