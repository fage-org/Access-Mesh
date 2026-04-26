package cn.ac.fage.accessmesh.admin.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginReq(
    @NotBlank(message = "租户ID不能为空")
    String tenantId,
    @NotBlank(message = "用户名不能为空")
    String username,
    @NotBlank(message = "密码不能为空")
    String password,
    String captchaId,
    String captchaCode,
    @NotBlank(message = "客户端ID不能为空")
    String clientId
) {}
