package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.MenuResp;
import cn.ac.fage.accessmesh.admin.service.MenuService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理控制器
 * <p>
 * 提供菜单的CRUD操作和树结构查询功能。
 * 菜单是系统的导航结构，用于组织页面和功能入口。
 * 菜单可以配置权限标识，用于按钮级别的权限控制。
 * 所有接口采用POST + JSON Body方式。
 * </p>
 */
@RestController
@RequestMapping("/menu")
public class MenuController {

    private final MenuService menuService;

    /**
     * 构造函数注入依赖
     *
     * @param menuService 菜单管理服务
     */
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 创建菜单
     * <p>
     * 创建新的菜单项，设置菜单名称、路径、图标、权限标识等属性。
     * </p>
     *
     * @param req 菜单创建请求，包含菜单基本信息
     * @return 创建成功的菜单ID
     */
    @PostMapping("/create")
    @AuditLog(module = "菜单管理", action = "创建", targetType = "MENU")
    public PermResult<Long> createMenu(@Valid @RequestBody MenuCreateReq req) {
        return PermResult.success(menuService.createMenu(req));
    }

    /**
     * 更新菜单信息
     * <p>
     * 更新菜单的名称、路径、图标、权限标识、状态等属性。
     * </p>
     *
     * @param req 菜单更新请求，包含菜单ID和新属性值
     * @return 操作成功结果
     */
    @PostMapping("/update")
    @AuditLog(module = "菜单管理", action = "修改", targetType = "MENU")
    public PermResult<Void> updateMenu(@Valid @RequestBody MenuUpdateReq req) {
        menuService.updateMenu(req);
        return PermResult.success();
    }

    /**
     * 删除菜单
     * <p>
     * 删除指定菜单，会同时处理子菜单和权限配置。
     * </p>
     *
     * @param req ID请求，包含菜单ID
     * @return 操作成功结果
     */
    @PostMapping("/delete")
    @AuditLog(module = "菜单管理", action = "删除", targetType = "MENU")
    public PermResult<Void> deleteMenu(@Valid @RequestBody IdReq req) {
        menuService.deleteMenu(req.id());
        return PermResult.success();
    }

    /**
     * 获取菜单详情
     * <p>
     * 根据菜单ID查询菜单的完整信息。
     * </p>
     *
     * @param req ID请求，包含菜单ID
     * @return 菜单详情信息
     */
    @PostMapping("/detail")
    public PermResult<MenuResp> getMenu(@Valid @RequestBody IdReq req) {
        return PermResult.success(menuService.getMenu(req.id()));
    }

    /**
     * 查询菜单树
     * <p>
     * 返回完整的菜单层级树结构，用于管理界面展示菜单组织关系。
     * </p>
     *
     * @return 菜单树结构列表
     */
    @PostMapping("/tree")
    public PermResult<List<MenuResp>> treeMenu() {
        return PermResult.success(menuService.treeMenu());
    }
}