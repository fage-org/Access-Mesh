package cn.ac.fage.accessmesh.admin.dto.req;

public record ConfigUpdateReq(
    Long id,
    String configValue,
    String remark
) {}
