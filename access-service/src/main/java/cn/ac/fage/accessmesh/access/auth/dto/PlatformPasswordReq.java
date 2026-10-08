package cn.ac.fage.accessmesh.access.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformPasswordReq(@NotBlank @Size(max=32) String oldPassword,
                                  @NotBlank @Size(min=8,max=32) String newPassword) {}
