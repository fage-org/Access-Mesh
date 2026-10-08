package cn.ac.fage.accessmesh.access.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformLoginReq(@NotBlank @Size(max=64) String username,
                               @NotBlank @Size(max=32) String password,
                               @NotBlank String captchaId, @NotBlank String captchaCode) {}
