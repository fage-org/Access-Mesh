package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
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

@RestController
@RequestMapping("/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping("/create")
    @AuditLog(module = "菜单管理", action = "创建", targetType = "MENU")
    public PermResult<Long> createMenu(@Valid @RequestBody MenuCreateReq req) {
        return PermResult.success(menuService.createMenu(req));
    }

    @PostMapping("/update")
    @AuditLog(module = "菜单管理", action = "修改", targetType = "MENU")
    public PermResult<Void> updateMenu(@Valid @RequestBody MenuUpdateReq req) {
        menuService.updateMenu(req);
        return PermResult.success();
    }

    @PostMapping("/delete")
    @AuditLog(module = "菜单管理", action = "删除", targetType = "MENU")
    public PermResult<Void> deleteMenu(@Valid @RequestBody IdReq req) {
        menuService.deleteMenu(req.id());
        return PermResult.success();
    }

    @PostMapping("/detail")
    public PermResult<MenuResp> getMenu(@Valid @RequestBody IdReq req) {
        return PermResult.success(menuService.getMenu(req.id()));
    }

    @PostMapping("/tree")
    public PermResult<List<MenuResp>> treeMenu() {
        return PermResult.success(menuService.treeMenu());
    }
}
