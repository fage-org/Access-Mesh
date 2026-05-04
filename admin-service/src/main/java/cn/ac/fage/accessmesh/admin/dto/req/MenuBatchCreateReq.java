package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量创建菜单请求
 */
public record MenuBatchCreateReq(
    @Valid @NotEmpty(message = "菜单列表不能为空")
    List<MenuCreateReq> menus
) {}