package cn.ac.fage.accessmesh.admin.dto.req;

public record MenuUpdateReq(
    Long id,
    Integer menuType,
    String menuName,
    Long parentId,
    String path,
    String component,
    String perms,
    String icon,
    Integer sort,
    Integer visible,
    Integer status
) {}
