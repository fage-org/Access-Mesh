package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.MenuResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.MenuService;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.access.application.MenuWriteAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单管理服务实现类
 * <p>
 * 提供菜单的CRUD操作、树形查询功能。
 * 写操作委托 {@code MenuWriteAppService} 同一事务维护 ADMIN_MENU 权限投影。
 * 支持菜单层级深度限制（最多5级）、路由路径与资源关联唯一性校验（v3.5 终态）。
 * 使用MenuDomainService处理菜单数据查询。
 * </p>
 */
@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuServiceImpl.class);

    private final SysMenuMapper menuMapper;
    private final MenuDomainService menuDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final MenuWriteAppService menuWriteAppService;

    /**
     * 构造函数注入依赖
     *
     * @param menuMapper 菜单数据访问Mapper
     * @param menuDomainService 菜单领域服务，处理菜单数据查询
     * @param permissionValidator 权限校验器，校验菜单操作权限
     * @param menuWriteAppService 菜单写编排，同一事务维护 ADMIN_MENU 投影
     */
    public MenuServiceImpl(SysMenuMapper menuMapper,
                           MenuDomainService menuDomainService,
                           AdminPermissionValidator permissionValidator,
                           MenuWriteAppService menuWriteAppService) {
        this.menuMapper = menuMapper;
        this.menuDomainService = menuDomainService;
        this.permissionValidator = permissionValidator;
        this.menuWriteAppService = menuWriteAppService;
    }

    /**
     * 创建菜单
     * <p>
     * 创建新菜单，校验路由路径/资源关联唯一性和菜单层级深度（不超过5级）。
     * ADMIN_MENU 投影由 {@code MenuWriteAppService} 同一事务维护。
     * </p>
     *
     * @param req 菜单创建请求，包含菜单类型、显示名、路径、资源关联等
     * @return 新菜单ID
     * @throws BizException 路径/资源关联已存在、菜单层级超限等
     */
    @Override
    public Long createMenu(MenuCreateReq req) {
        return menuWriteAppService.createMenu(req);
    }

    /**
     * 更新菜单
     * <p>
     * 更新菜单的各项属性，校验路由路径/资源关联唯一性和菜单层级深度。
     * 执行实例级权限校验；投影由 {@code MenuWriteAppService} 同一事务维护。
     * </p>
     *
     * @param req 菜单更新请求，包含菜单ID和新属性值
     * @throws BizException 菜单不存在、路径/资源关联已存在、菜单层级超限等
     */
    @Override
    public void updateMenu(MenuUpdateReq req) {
        menuWriteAppService.updateMenu(req);
    }

    /**
     * 删除菜单
     * <p>
     * 软删除菜单，不允许删除有子菜单的菜单。
     * 执行实例级权限校验；投影清理由 {@code MenuWriteAppService} 同一事务维护。
     * </p>
     *
     * @param id 菜单ID
     * @throws BizException 菜单不存在、有子菜单等
     */
    @Override
    public void deleteMenu(Long id) {
        menuWriteAppService.deleteMenu(id);
    }

    /**
     * 获取菜单详情
     * <p>
     * 根据菜单ID查询菜单完整信息。
     * </p>
     *
     * @param id 菜单ID
     * @return 菜单详情响应
     * @throws BizException 菜单不存在
     */
    @Override
    public MenuResp getMenu(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取菜单
        SysMenu menu = menuDomainService.selectValidById(tenantId, id);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        return toResp(menu, List.of());
    }

    /**
     * 查询菜单树
     * <p>
     * 获取当前租户的所有菜单，构建树形结构返回。
     * 按排序字段和创建时间排序。
     * </p>
     *
     * @return 菜单树列表
     */
    @Override
    public List<MenuResp> treeMenu() {
        Long tenantId = TenantContextHolder.getTenantId();
        List<SysMenu> all = menuMapper.selectMenusForTree(tenantId);
        return buildTree(all, 0L);
    }

    /**
     * 将菜单实体转换为响应对象
     * <p>
     * 转换菜单实体为API响应格式（v3.5 终态字段），包含子菜单列表。
     * </p>
     *
     * @param menu 菜单实体
     * @param children 子菜单响应列表
     * @return 菜单响应对象
     */
    private MenuResp toResp(SysMenu menu, List<MenuResp> children) {
        return new MenuResp(
            menu.getId(), menu.getMenuType(), menu.getDisplayName(),
            menu.getParentId(), menu.getPath(), menu.getIcon(), menu.getSortOrder(),
            menu.getStatus(), menu.getResourceType(), menu.getResourceCode(),
            menu.getSourceService(), menu.getCreatedAt(), menu.getUpdatedAt(), children
        );
    }

    /**
     * 构建菜单树
     * <p>
     * 将菜单列表转换为树形结构，递归构建子菜单。
     * </p>
     *
     * @param all 所有菜单列表
     * @param parentId 当前层级父菜单ID（0表示根级）
     * @return 菜单树列表
     */
    private List<MenuResp> buildTree(List<SysMenu> all, Long parentId) {
        return all.stream()
            .filter(m -> parentId.equals(m.getParentId()))
            .map(m -> new MenuResp(
                m.getId(), m.getMenuType(), m.getDisplayName(),
                m.getParentId(), m.getPath(), m.getIcon(), m.getSortOrder(),
                m.getStatus(), m.getResourceType(), m.getResourceCode(),
                m.getSourceService(), m.getCreatedAt(), m.getUpdatedAt(),
                buildTree(all, m.getId())
            ))
            .collect(Collectors.toList());
    }
}
