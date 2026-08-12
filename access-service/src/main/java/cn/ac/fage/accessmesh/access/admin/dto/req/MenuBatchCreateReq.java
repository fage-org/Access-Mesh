package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量创建菜单请求记录类
 * <p>
 * 用于一次性创建多个菜单的请求参数。
 * 常用于初始化系统菜单或导入菜单配置。
 * </p>
 *
 * @param menus 菜单创建请求列表（必填，至少包含一个菜单）
 */
public record MenuBatchCreateReq(
    /**
     * 菜单创建请求列表
     */
    @Valid @NotEmpty(message = "菜单列表不能为空")
    List<MenuCreateReq> menus
) {}