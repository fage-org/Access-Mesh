package cn.ac.fage.accessmesh.admin.dto.req;

public record UserQuery(
    String username,
    String name,
    String phone,
    String email,
    Integer status
) {}
