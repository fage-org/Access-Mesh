package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MenuCreateReq(
    @NotNull(message = "菜单类型不能为空")
    Integer menuType,
    @NotBlank(message = "菜单名称不能为空")
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
