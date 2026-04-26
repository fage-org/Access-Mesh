package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

public record UserCreateReq(
    @NotBlank(message = "用户名不能为空")
    String username,
    @NotBlank(message = "姓名不能为空")
    String name,
    String phone,
    String email,
    Integer status
) {}
