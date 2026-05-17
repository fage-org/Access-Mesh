package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.MenuResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.MenuService;
import cn.ac.fage.accessmesh.admin.service.SyncRetryService;
import cn.ac.fage.accessmesh.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.MenuSyncHandler;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单管理服务实现类
 * <p>
 * 提供菜单的CRUD操作、树形查询功能。
 * 实现跨服务数据同步机制，通过记录同步任务模式确保菜单变更同步到permission-center。
 * 支持菜单层级深度限制（最多5级）、权限标识唯一性校验。
 * 使用MenuDomainService处理菜单数据查询。
 * </p>
 */
@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuServiceImpl.class);

    private final SysMenuMapper menuMapper;
    private final MenuDomainService menuDomainService;
    private final MenuSyncHandler menuSyncHandler;
    private final AdminPermissionValidator permissionValidator;
    private final SyncRetryService syncRetryService;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param menuMapper 菜单数据访问Mapper
     * @param menuDomainService 菜单领域服务，处理菜单数据查询
     * @param menuSyncHandler 菜单同步处理器，同步菜单数据到permission-center
     * @param permissionValidator 权限校验器，校验菜单操作权限
     * @param syncRetryService 同步重试服务，记录同步失败任务
     * @param objectMapper JSON序列化工具
     */
    public MenuServiceImpl(SysMenuMapper menuMapper,
                           MenuDomainService menuDomainService,
                           MenuSyncHandler menuSyncHandler,
                           AdminPermissionValidator permissionValidator,
                           SyncRetryService syncRetryService,
                           ObjectMapper objectMapper) {
        this.menuMapper = menuMapper;
        this.menuDomainService = menuDomainService;
        this.menuSyncHandler = menuSyncHandler;
        this.permissionValidator = permissionValidator;
        this.syncRetryService = syncRetryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建菜单
     * <p>
     * 创建新菜单，校验权限标识唯一性和菜单层级深度（不超过5级）。
     * 创建成功后记录同步任务，异步同步到permission-center。
     * </p>
     *
     * @param req 菜单创建请求，包含菜单名称、路径、组件、权限标识等
     * @return 新菜单ID
     * @throws BizException 权限标识已存在、菜单层级超限、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMenu(MenuCreateReq req) {
        // 权限检查 — 类型级 CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.MENU, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 检查权限标识重复
        if (req.perms() != null && !req.perms().isBlank()) {
            SysMenu existing = menuDomainService.findByPermCode(tenantId, req.perms());
            if (existing != null) {
                throw new BizException(AdminErrorCode.MENU_PERM_CODE_EXISTS.getCode(),
                    AdminErrorCode.MENU_PERM_CODE_EXISTS.getMessage());
            }
        }

        // 使用 DomainService 计算深度
        int depth = menuDomainService.calculateDepth(tenantId, req.parentId());
        if (depth > 5) {
            throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
        }

        SysMenu menu = new SysMenu();
        menu.setTenantId(tenantId);
        menu.setParentId(req.parentId() != null ? req.parentId() : 0L);
        menu.setMenuType(String.valueOf(req.menuType()));
        menu.setName(req.menuName());
        menu.setPath(req.path());
        menu.setComponent(req.component());
        menu.setPermCode(req.perms());
        menu.setIcon(req.icon());
        menu.setSortOrder(req.sort());
        menu.setVisible(req.visible() != null && req.visible() == 1);
        menu.setStatus(req.status() != null ? req.status() : 1);
        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());
        menu.setDeleteFlag(0L);
        menuMapper.insert(menu);

        // 记录同步任务，异步同步到权限中心
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "menuId", menu.getId(),
                "menuName", menu.getName(),
                "tenantId", tenantId
            ));
            syncRetryService.recordSyncFailure(
                "menu:create:" + menu.getId(),
                "permission-center",
                "resource_entity",
                String.valueOf(menu.getId()),
                "create",
                payload,
                null  // 不记录错误，只是记录待同步任务
            );
            log.info("Recorded sync task for menu creation: menuId={}", menu.getId());
        } catch (Exception e) {
            log.error("Failed to record sync task for menu creation: menuId={}, error={}", menu.getId(), e.getMessage());
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "菜单同步任务记录失败");
        }

        return menu.getId();
    }

    /**
     * 更新菜单
     * <p>
     * 更新菜单的各项属性，校验权限标识唯一性和菜单层级深度。
     * 执行实例级权限校验。
     * 如果菜单已关联permission-center或设置了权限标识，同步更新到permission-center。
     * </p>
     *
     * @param req 菜单更新请求，包含菜单ID和新属性值
     * @throws BizException 菜单不存在、权限标识已存在、菜单层级超限等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMenu(MenuUpdateReq req) {
        // 权限检查 — 实例级 UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.MENU,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取菜单
        SysMenu menu = menuDomainService.selectValidById(tenantId, req.id());
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }

        // 使用 DomainService 检查权限标识重复
        if (req.perms() != null && !req.perms().isBlank() && !req.perms().equals(menu.getPermCode())) {
            SysMenu existing = menuDomainService.findByPermCode(tenantId, req.perms());
            if (existing != null) {
                throw new BizException(AdminErrorCode.MENU_PERM_CODE_EXISTS.getCode(),
                    AdminErrorCode.MENU_PERM_CODE_EXISTS.getMessage());
            }
        }

        // 使用 DomainService 检查新父菜单深度
        if (req.parentId() != null && !req.parentId().equals(menu.getParentId())) {
            int depth = menuDomainService.calculateDepth(tenantId, req.parentId());
            if (depth > 5) {
                throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                    AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
            }
        }

        menu.setMenuType(req.menuType() != null ? String.valueOf(req.menuType()) : menu.getMenuType());
        menu.setName(req.menuName());
        menu.setParentId(req.parentId() != null ? req.parentId() : menu.getParentId());
        menu.setPath(req.path());
        menu.setComponent(req.component());
        menu.setPermCode(req.perms());
        menu.setIcon(req.icon());
        menu.setSortOrder(req.sort());
        menu.setVisible(req.visible() != null && req.visible() == 1);
        menu.setStatus(req.status() != null ? req.status() : menu.getStatus());
        menu.setUpdatedAt(LocalDateTime.now());
        menuMapper.update(menu);

        // 使用 SyncHandler 同步更新到权限中心
        if (menu.getPermResourceId() != null || (req.perms() != null && !req.perms().isBlank())) {
            menuSyncHandler.syncMenuToPermissionCenter(tenantId, menu);
        }
    }

    /**
     * 删除菜单
     * <p>
     * 软删除菜单，不允许删除有子菜单的菜单。
     * 执行实例级权限校验，先本地软删除再记录同步任务。
     * </p>
     *
     * @param id 菜单ID
     * @throws BizException 菜单不存在、有子菜单、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(Long id) {
        // 权限检查 — 实例级 DELETE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.MENU,
            String.valueOf(id),
            AdminOperationCode.DELETE
        );

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取菜单
        SysMenu menu = menuDomainService.selectValidById(tenantId, id);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }

        // 使用 DomainService 检查是否有子菜单
        if (menuDomainService.hasChildren(tenantId, id)) {
            throw new BizException(AdminErrorCode.MENU_HAS_CHILDREN.getCode(), AdminErrorCode.MENU_HAS_CHILDREN.getMessage());
        }

        // 1. 先执行本地软删除
        menuDomainService.softDeleteBatch(tenantId, List.of(id));

        // 2. 记录删除同步任务
        try {
            syncRetryService.recordSyncFailure(
                "menu:delete:" + id,
                "permission-center",
                "resource_entity",
                String.valueOf(id),
                "delete",
                null,
                null
            );
            log.info("Recorded delete sync task for menu: menuId={}", id);
        } catch (Exception e) {
            log.error("Failed to record delete sync task for menu: menuId={}, error={}", id, e.getMessage());
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "菜单同步任务记录失败");
        }
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
     * 转换菜单实体为API响应格式，包含子菜单列表。
     * </p>
     *
     * @param menu 菜单实体
     * @param children 子菜单响应列表
     * @return 菜单响应对象
     */
    private MenuResp toResp(SysMenu menu, List<MenuResp> children) {
        return new MenuResp(
            menu.getId(), Integer.parseInt(menu.getMenuType()), menu.getName(),
            menu.getParentId(), menu.getPath(), menu.getComponent(), menu.getPermCode(),
            menu.getIcon(), menu.getSortOrder(), menu.getVisible() ? 1 : 0,
            menu.getStatus(), menu.getCreatedAt(), menu.getUpdatedAt(), children
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
                m.getId(), Integer.parseInt(m.getMenuType()), m.getName(),
                m.getParentId(), m.getPath(), m.getComponent(), m.getPermCode(),
                m.getIcon(), m.getSortOrder(), m.getVisible() ? 1 : 0,
                m.getStatus(), m.getCreatedAt(), m.getUpdatedAt(),
                buildTree(all, m.getId())
            ))
            .collect(Collectors.toList());
    }
}