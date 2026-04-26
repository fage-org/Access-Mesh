package cn.ac.fage.accessmesh.admin.dto.resp;

import java.time.LocalDateTime;
import java.util.List;

public record MenuResp(
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
    Integer status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<MenuResp> children
) {}
