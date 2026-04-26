package cn.ac.fage.accessmesh.admin.dto.req;

public record UserUpdateReq(
    Long id,
    String name,
    String phone,
    String email,
    Integer status
) {}
