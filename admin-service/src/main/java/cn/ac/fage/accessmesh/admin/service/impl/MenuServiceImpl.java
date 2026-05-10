package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.BatchResultResp;
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
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cn.ac.fage.accessmesh.admin.entity.table.SysMenuTableDef;

/**
 * 菜单管理服务实现类
 * <p>
 * 提供菜单的CRUD操作、树形查询、批量操作等功能。
 * 实现跨服务数据同步机制，通过记录同步任务模式确保菜单变更同步到permission-center。
 * 支持菜单层级深度限制（最多5级）、权限标识唯一性校验。
 * 使用MenuDomainService处理菜单数据查询和批量操作。
 * </p>
 */
@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuServiceImpl.class);

    private final SysMenuMapper menuMapper;
    private final cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService;
    private final MenuDomainService menuDomainService;
    private final MenuSyncHandler menuSyncHandler;
    private final AdminPermissionValidator permissionValidator;
    private final SyncRetryService syncRetryService;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param menuMapper 菜单数据访问Mapper
     * @param roleProxyService 角色代理服务，获取用户角色和权限
     * @param menuDomainService 菜单领域服务，处理菜单数据查询和批量操作
     * @param menuSyncHandler 菜单同步处理器，同步菜单数据到permission-center
     * @param permissionValidator 权限校验器，校验菜单操作权限
     * @param syncRetryService 同步重试服务，记录同步失败任务
     * @param objectMapper JSON序列化工具
     */
    public MenuServiceImpl(SysMenuMapper menuMapper,
                           cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService,
                           MenuDomainService menuDomainService,
                           MenuSyncHandler menuSyncHandler,
                           AdminPermissionValidator permissionValidator,
                           SyncRetryService syncRetryService,
                           ObjectMapper objectMapper) {
        this.menuMapper = menuMapper;
        this.roleProxyService = roleProxyService;
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
        // Permission check - type-level CREATE
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
                "abstract_user",
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
        // Permission check - instance-level UPDATE
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
        // Permission check - instance-level DELETE
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
                "abstract_user",
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
        List<SysMenu> all = menuMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysMenuTableDef.SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SysMenuTableDef.SYS_MENU.DELETE_FLAG.eq(0))
                .orderBy(SysMenuTableDef.SYS_MENU.SORT_ORDER.asc(), SysMenuTableDef.SYS_MENU.CREATED_AT.asc())
        );
        return buildTree(all, 0L);
    }

    /**
     * 获取用户按钮级权限列表
     * <p>
     * 通过RoleProxyService加载用户的角色和权限，返回按钮级权限列表。
     * </p>
     *
     * @param userId 用户ID
     * @return 权限码列表
     */
    @Override
    public List<String> getUserPermissions(Long userId) {
        cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp info = roleProxyService.loadUserRolesAndPermissions(userId);
        return info != null ? info.permissions() : List.of();
    }

    /**
     * 批量创建菜单
     * <p>
     * 批量创建多个菜单，校验权限标识唯一性和菜单层级深度。
     * 使用批量查询检查权限标识和父菜单深度（优化性能）。
     * 返回部分成功结果，包含成功ID列表和失败消息列表。
     * </p>
     *
     * @param req 批量创建请求，包含多个菜单创建请求
     * @return 批量操作结果，包含成功ID列表和失败消息列表
     * @throws BizException 同步任务记录失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchResultResp batchCreateMenus(MenuBatchCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.MENU, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();
        List<Long> successIds = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        // Performance fix: Batch collect all permCodes and parentIds
        Set<String> allPermCodes = req.menus().stream()
            .map(MenuCreateReq::perms)
            .filter(p -> p != null && !p.isBlank())
            .collect(Collectors.toSet());
        Set<Long> allParentIds = req.menus().stream()
            .map(MenuCreateReq::parentId)
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toSet());

        // Batch query existing permCodes and parent depths
        Set<String> existingPermCodes = menuDomainService.findExistingPermCodes(tenantId, allPermCodes);
        Map<Long, Integer> parentDepthMap = menuDomainService.batchCalculateDepth(tenantId, allParentIds);

        // Build menus to insert
        List<SysMenu> menusToInsert = new ArrayList<>();
        List<MenuCreateReq> validMenuReqs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (MenuCreateReq menuReq : req.menus()) {
            // Check permCode duplicate
            if (menuReq.perms() != null && !menuReq.perms().isBlank()
                && existingPermCodes.contains(menuReq.perms())) {
                failedMessages.add("权限标识已存在: " + menuReq.perms());
                continue;
            }

            // Calculate depth
            int depth = 1;
            if (menuReq.parentId() != null && menuReq.parentId() > 0) {
                Integer parentDepth = parentDepthMap.get(menuReq.parentId());
                if (parentDepth == null) {
                    failedMessages.add("父菜单不存在: " + menuReq.parentId());
                    continue;
                }
                depth = parentDepth + 1;
            }

            if (depth > 5) {
                failedMessages.add("菜单层级超过限制: " + menuReq.menuName());
                continue;
            }

            // Build menu entity
            SysMenu menu = new SysMenu();
            menu.setTenantId(tenantId);
            menu.setParentId(menuReq.parentId() != null && menuReq.parentId() > 0 ? menuReq.parentId() : 0L);
            menu.setMenuType(String.valueOf(menuReq.menuType()));
            menu.setName(menuReq.menuName());
            menu.setPath(menuReq.path());
            menu.setComponent(menuReq.component());
            menu.setPermCode(menuReq.perms());
            menu.setIcon(menuReq.icon());
            menu.setSortOrder(menuReq.sort());
            menu.setVisible(menuReq.visible() != null && menuReq.visible() == 1);
            menu.setStatus(menuReq.status() != null ? menuReq.status() : 1);
            menu.setCreatedAt(now);
            menu.setUpdatedAt(now);
            menu.setDeleteFlag(0L);

            menusToInsert.add(menu);
            validMenuReqs.add(menuReq);
        }

        // Batch insert
        if (!menusToInsert.isEmpty()) {
            menuDomainService.insertBatch(menusToInsert);

            // Record sync tasks for inserted menus
            for (int i = 0; i < menusToInsert.size(); i++) {
                SysMenu menu = menusToInsert.get(i);
                successIds.add(menu.getId());

                try {
                    String payload = objectMapper.writeValueAsString(Map.of(
                        "menuId", menu.getId(),
                        "menuName", menu.getName(),
                        "tenantId", tenantId
                    ));
                    syncRetryService.recordSyncFailure(
                        "menu:create:" + menu.getId(),
                        "permission-center",
                        "abstract_user",
                        String.valueOf(menu.getId()),
                        "create",
                        payload,
                        null
                    );
                } catch (Exception syncEx) {
                    log.error("Failed to record sync task for batch menu creation: menuId={}, error={}",
                        menu.getId(), syncEx.getMessage());
                    throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "菜单同步任务记录失败");
                }
            }
        }

        return BatchResultResp.partial(req.menus().size(), successIds.size(), successIds, failedMessages);
    }

    /**
     * 批量删除菜单
     * <p>
     * 批量软删除菜单及其所有子菜单。
     * 执行批量实例级权限校验，使用批量查询获取子菜单ID。
     * 先本地软删除再记录同步任务。
     * </p>
     *
     * @param req ID集合请求，包含待删除的菜单ID列表
     * @throws BizException 同步任务记录失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteMenus(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.MENU, resourceCodes, AdminOperationCode.DELETE);

        Long tenantId = TenantContextHolder.getTenantId();

        // Performance fix: Batch load menus and descendants
        Set<Long> menuIdSet = new java.util.HashSet<>(req.ids());
        List<SysMenu> menus = menuDomainService.selectValidByIds(tenantId, menuIdSet);

        // Get all descendant IDs in batch
        Map<Long, List<Long>> descendantMap = menuDomainService.batchGetDescendantIds(tenantId, menuIdSet);

        // Collect all IDs to delete (including self)
        Set<Long> allIdsToDelete = new java.util.HashSet<>();
        for (SysMenu menu : menus) {
            Long menuId = menu.getId();
            // Add self
            allIdsToDelete.add(menuId);
            // Add descendants
            List<Long> descendants = descendantMap.getOrDefault(menuId, List.of());
            allIdsToDelete.addAll(descendants);
        }

        // 1. 先执行本地批量软删除
        if (!allIdsToDelete.isEmpty()) {
            menuDomainService.softDeleteBatch(tenantId, List.copyOf(allIdsToDelete));
        }

        // 2. 记录删除同步任务
        for (SysMenu menu : menus) {
            try {
                syncRetryService.recordSyncFailure(
                    "menu:delete:" + menu.getId(),
                    "permission-center",
                    "abstract_user",
                    String.valueOf(menu.getId()),
                    "delete",
                    null,
                    null
                );
                log.info("Recorded delete sync task for menu: menuId={}", menu.getId());
            } catch (Exception e) {
                log.error("Failed to record delete sync task for menu: menuId={}, error={}", menu.getId(), e.getMessage());
                throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "菜单同步任务记录失败");
            }
        }
    }

    /**
     * 获取菜单的所有子菜单ID（包含自身）
     * <p>
     * 递归查询菜单的所有后代菜单ID，用于级联删除等操作。
     * </p>
     *
     * @param menuId 菜单ID
     * @return 子菜单ID列表（包含自身）
     */
    @Override
    public List<Long> getDescendantMenuIds(Long menuId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return menuDomainService.getDescendantIdsIncludingSelf(tenantId, menuId);
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