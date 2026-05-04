package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.BatchResultResp;
import cn.ac.fage.accessmesh.admin.dto.resp.MenuResp;

import java.util.List;

public interface MenuService {

    Long createMenu(MenuCreateReq req);

    BatchResultResp batchCreateMenus(MenuBatchCreateReq req);

    void updateMenu(MenuUpdateReq req);

    void deleteMenu(Long id);

    void batchDeleteMenus(IdsReq req);

    MenuResp getMenu(Long id);

    List<MenuResp> treeMenu();

    List<String> getUserPermissions(Long userId);

    List<Long> getDescendantMenuIds(Long menuId);
}
