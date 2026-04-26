package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.MenuResp;
import cn.ac.fage.accessmesh.common.model.PermResult;

import java.util.List;

public interface MenuService {

    Long createMenu(MenuCreateReq req);

    void updateMenu(MenuUpdateReq req);

    void deleteMenu(Long id);

    MenuResp getMenu(Long id);

    List<MenuResp> treeMenu();

    List<String> getUserPermissions(Long userId);
}
