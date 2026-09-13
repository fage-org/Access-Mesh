package cn.ac.fage.accessmesh.access.menu.service;

import cn.ac.fage.accessmesh.access.menu.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.access.menu.dto.req.MenuUpdateReq;

/**
 * 菜单跨域写编排：DIR/MENU 同行事务写入 MENU 投影；BUTTON 不投影。
 */
public interface MenuWriteAppService {

    Long createMenu(MenuCreateReq req);

    void updateMenu(MenuUpdateReq req);

    void deleteMenu(Long id);
}
