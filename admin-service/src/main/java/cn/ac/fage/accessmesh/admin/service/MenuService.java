package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.MenuResp;

import java.util.List;

/**
 * 菜单服务接口
 * <p>
 * 提供菜单管理相关的服务方法，包括菜单的创建、更新、删除、查询等。
 * 支持菜单树结构查询。
 * </p>
 */
public interface MenuService {

    /**
     * 创建菜单
     * <p>
     * 创建单个菜单项。
     * 用于手动添加菜单配置。
     * </p>
     *
     * @param req 菜单创建请求
     * @return 创建的菜单ID
     */
    Long createMenu(MenuCreateReq req);

    /**
     * 更新菜单
     * <p>
     * 更新指定菜单的基本信息。
     * 包括菜单名称、路径、图标、排序等属性。
     * </p>
     *
     * @param req 菜单更新请求
     */
    void updateMenu(MenuUpdateReq req);

    /**
     * 删除菜单
     * <p>
     * 删除指定菜单及其所有子菜单。
     * 使用软删除方式，保留数据记录。
     * </p>
     *
     * @param id 菜单ID
     */
    void deleteMenu(Long id);

    /**
     * 获取菜单详情
     * <p>
     * 根据ID查询菜单详细信息。
     * </p>
     *
     * @param id 菜单ID
     * @return 菜单详情响应
     */
    MenuResp getMenu(Long id);

    /**
     * 获取菜单树
     * <p>
     * 获取完整的菜单树结构。
     * 用于菜单管理界面的树形展示。
     * </p>
     *
     * @return 菜单树列表
     */
    List<MenuResp> treeMenu();
}