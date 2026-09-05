package cn.ac.fage.accessmesh.access.application.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.access.application.MenuWriteAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单写链路编排（v3.5 菜单零权限化终态，T-ACCESS-015）。
 * <p>
 * sys_menu 仅承载 UI 路由元数据与资源 link：BUTTON 类型与 perm_code 唯一性
 * 校验已随权威 DDL 移除；唯一性由 uk_sys_menu_tenant_path /
 * uk_sys_menu_tenant_resource 承接（写前预查 + 唯一索引冲突按约束名映射错误码）。
 * MENU 投影对 DIR/MENU/EXTERNAL/IFRAME/HIDDEN 全量维护
 * （实例级管理门禁依赖投影行授权到具体菜单实例；菜单可见性由 v3.5 §4.1
 * 派生公式在 UserMenuQueryService 读链路决定，不消费投影）。
 * </p>
 */
@Service
public class MenuWriteAppServiceImpl implements MenuWriteAppService {

    private static final String UK_MENU_PATH = "uk_sys_menu_tenant_path";
    private static final String UK_MENU_RESOURCE = "uk_sys_menu_tenant_resource";
    /** 菜单树最大层级（根为第 1 层，向下最多 5 层） */
    private static final int MAX_MENU_DEPTH = 5;

    private final MenuDomainService menuDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final AuditDomainService auditDomainService;
    private final TreeWriteLockSupport treeWriteLockSupport;

    public MenuWriteAppServiceImpl(MenuDomainService menuDomainService,
                                   AdminPermissionValidator permissionValidator,
                                   LocalProjectionDomainService localProjectionDomainService,
                                   AuditDomainService auditDomainService,
                                   TreeWriteLockSupport treeWriteLockSupport) {
        this.menuDomainService = menuDomainService;
        this.permissionValidator = permissionValidator;
        this.localProjectionDomainService = localProjectionDomainService;
        this.auditDomainService = auditDomainService;
        this.treeWriteLockSupport = treeWriteLockSupport;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "MENU_CREATE", targetType = "sys_menu",
        targetId = "#result", summary = "'create menu ' + #req.displayName()")
    public Long createMenu(MenuCreateReq req) {
        permissionValidator.checkTypeLevel(ResourceTypeCode.MENU, AdminOperationCode.CREATE);
        Long tenantId = TenantContextHolder.getTenantId();
        // 空白字符串规范化为 null（用户决策 2026-08-22）：空串入库会命中部分唯一索引
        // （WHERE col IS NOT NULL 对 '' 生效）并被读链路误判为业务菜单 fail-closed
        String path = normalize(req.path());
        String resourceType = normalize(req.resourceType());
        String resourceCode = normalize(req.resourceCode());
        String sourceService = normalize(req.sourceService());
        checkUnique(tenantId, path, resourceType, resourceCode, null);
        checkParentExists(tenantId, req.parentId());
        // calculateDepth 返回父节点自身深度（顶级=1），新节点深度 = 父深度 + 1
        if (menuDomainService.calculateDepth(tenantId, req.parentId()) + 1 > MAX_MENU_DEPTH) {
            throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
        }
        SysMenu menu = new SysMenu();
        menu.setTenantId(tenantId);
        menu.setParentId(req.parentId() != null ? req.parentId() : 0L);
        menu.setMenuType(req.menuType());
        menu.setDisplayName(req.displayName());
        menu.setPath(path);
        menu.setIcon(normalize(req.icon()));
        menu.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        menu.setStatus(req.status() != null ? req.status() : 1);
        menu.setResourceType(resourceType);
        menu.setResourceCode(resourceCode);
        menu.setSourceService(sourceService != null ? sourceService : LocalProjectionOwner.SERVICE_CODE);
        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());
        menu.setDeleteFlag(0L);
        try {
            menuDomainService.insert(menu);
        } catch (DuplicateKeyException e) {
            rethrowIfUniqueViolation(e);
        }
        projectMenu(tenantId, menu);
        return menu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "MENU_UPDATE", targetType = "sys_menu",
        targetId = "#req.id()", summary = "'update menu ' + #req.id()")
    public void updateMenu(MenuUpdateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        // 树写锁先于首次实体读取并无条件持有（对齐 updateRole/updateResource 位置）：不带
        // parentId 的普通编辑也会全列回写实体快照的 parent（update(entity) 非 null 列全写），
        // 若锁晚于读取，读取-拿锁-写回窗口内完成的合法移动会被旧快照静默回滚、经两步合法
        // 移动+回写可闭合成环——锁覆盖读与写后，锁内快照在临界区内无并发变更
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.SYS_MENU);
        permissionValidator.checkInstanceLevel(
            ResourceTypeCode.MENU, String.valueOf(req.id()), AdminOperationCode.UPDATE);
        SysMenu menu = menuDomainService.selectValidById(tenantId, req.id());
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(),
                AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        // 空白规范化为 null；null 跳过保留原值（与部分更新语义一致）
        String path = normalize(req.path());
        String resourceType = normalize(req.resourceType());
        String resourceCode = normalize(req.resourceCode());
        // 唯一性预查按目标值（提供 path/resource 时）排除自身
        checkUnique(tenantId,
            path != null ? path : menu.getPath(),
            resourceType != null ? resourceType : menu.getResourceType(),
            resourceCode != null ? resourceCode : menu.getResourceCode(),
            req.id());
        // 请求携带 parentId（含表单回传原值）时按锁内快照重判换父——快照在锁内，无需二次读取
        if (req.parentId() != null && !req.parentId().equals(menu.getParentId())) {
            checkNewParent(tenantId, req.id(), req.parentId());
            // 换父按整棵子树校验：最深节点绝对深度 = 父深度 + 子树高度。
            // 父深度：顶级目标（parentId=0）按 0 计（新根自身即第 1 层，calculateDepth(0)=1
            // 会把不存在的父层多算一层）；非顶级为父节点自身深度。
            // （单节点子树高度为 1，等价于仅校验新根自身深度）
            int parentDepth = req.parentId() == 0L ? 0
                : menuDomainService.calculateDepth(tenantId, req.parentId());
            if (parentDepth + menuDomainService.subtreeHeight(tenantId, req.id()) > MAX_MENU_DEPTH) {
                throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                    AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
            }
        }
        // 可选字段仅更新提供的字段（规范化后 null 跳过，保留原值）；sourceService 创建期追溯标识，不可改
        if (req.menuType() != null) {
            menu.setMenuType(req.menuType());
        }
        if (req.displayName() != null) {
            menu.setDisplayName(req.displayName());
        }
        menu.setParentId(req.parentId() != null ? req.parentId() : menu.getParentId());
        if (path != null) {
            menu.setPath(path);
        }
        String icon = normalize(req.icon());
        if (icon != null) {
            menu.setIcon(icon);
        }
        if (req.sortOrder() != null) {
            menu.setSortOrder(req.sortOrder());
        }
        if (req.status() != null) {
            menu.setStatus(req.status());
        }
        if (resourceType != null) {
            menu.setResourceType(resourceType);
        }
        if (resourceCode != null) {
            menu.setResourceCode(resourceCode);
        }
        menu.setUpdatedAt(LocalDateTime.now());
        try {
            menuDomainService.update(menu);
        } catch (DuplicateKeyException e) {
            rethrowIfUniqueViolation(e);
        }
        projectMenu(tenantId, menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "MENU_DELETE", targetType = "sys_menu",
        targetId = "#id", summary = "'delete menu ' + #id")
    public void deleteMenu(Long id) {
        permissionValidator.checkInstanceLevel(
            ResourceTypeCode.MENU, String.valueOf(id), AdminOperationCode.DELETE);
        Long tenantId = TenantContextHolder.getTenantId();
        SysMenu menu = menuDomainService.selectValidById(tenantId, id);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(),
                AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        if (menuDomainService.hasChildren(tenantId, id)) {
            throw new BizException(AdminErrorCode.MENU_HAS_CHILDREN.getCode(),
                AdminErrorCode.MENU_HAS_CHILDREN.getMessage());
        }
        menuDomainService.softDeleteBatch(tenantId, List.of(id));
        // entityId 用 MENU 投影主键（resource_entity.id），不再用 sys_menu.id
        Long resourceId = localProjectionDomainService.findAdminMenuResourceId(tenantId, id);
        localProjectionDomainService.deleteAdminMenu(tenantId, id);
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "resource_entity", resourceId, "DELETE", null, null, null, new Long[0], new Long[0])));
    }

    /**
     * 写前唯一性预查：path 非空查 uk_sys_menu_tenant_path，
     * resourceType 非空查 uk_sys_menu_tenant_resource（排除自身）。
     */
    private void checkUnique(Long tenantId, String path, String resourceType, String resourceCode,
                             Long excludeId) {
        if (path != null && !path.isBlank()
            && menuDomainService.pathExists(tenantId, path, excludeId)) {
            throw new BizException(AdminErrorCode.MENU_PATH_EXISTS.getCode(),
                AdminErrorCode.MENU_PATH_EXISTS.getMessage());
        }
        if (resourceType != null && !resourceType.isBlank()
            && menuDomainService.resourceExists(tenantId, resourceType, resourceCode, excludeId)) {
            throw new BizException(AdminErrorCode.MENU_RESOURCE_EXISTS.getCode(),
                AdminErrorCode.MENU_RESOURCE_EXISTS.getMessage());
        }
    }

    /**
     * 并发窗口兜底：唯一索引冲突按约束名映射友好错误码并抛出；
     * 无法识别的约束按原异常抛出（事务回滚，全局兜底）。
     */
    private static void rethrowIfUniqueViolation(DuplicateKeyException e) {
        String message = e.getMostSpecificCause() != null && e.getMostSpecificCause().getMessage() != null
            ? e.getMostSpecificCause().getMessage() : String.valueOf(e.getMessage());
        if (message.contains(UK_MENU_PATH)) {
            throw new BizException(AdminErrorCode.MENU_PATH_EXISTS.getCode(),
                AdminErrorCode.MENU_PATH_EXISTS.getMessage());
        }
        if (message.contains(UK_MENU_RESOURCE)) {
            throw new BizException(AdminErrorCode.MENU_RESOURCE_EXISTS.getCode(),
                AdminErrorCode.MENU_RESOURCE_EXISTS.getMessage());
        }
        throw e;
    }

    /**
     * MENU 投影全量维护（v3.5 五值枚举无 BUTTON 短路）。
     */
    private void projectMenu(Long tenantId, SysMenu menu) {
        Long resourceId = localProjectionDomainService.upsertAdminMenu(
            tenantId, menu.getId(), menu.getDisplayName(), menu.getParentId(),
            menu.getStatus(), menu.getSortOrder());
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "resource_entity", resourceId, "UPSERT", null, null, null, new Long[0], new Long[0])));
    }

    /**
     * 创建时校验父菜单存在（正数 parentId 无外键兜底，不存在会写出从根不可达的孤儿节点，
     * 且 calculateDepth 对查不到的父返回 1 无法拦截）。
     */
    private void checkParentExists(Long tenantId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (menuDomainService.selectValidById(tenantId, parentId) == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), "父菜单不存在");
        }
    }

    /**
     * 换父时校验新父菜单：必须存在（同 {@link #checkParentExists}），
     * 且不能是被移动菜单自身或其后代（parent 链成环后环节点从树构建中静默消失、
     * 深度/祖先查询语义受损，sys_menu 无数据库约束兜底）。
     */
    private void checkNewParent(Long tenantId, Long menuId, Long newParentId) {
        checkParentExists(tenantId, newParentId);
        if (newParentId != null && newParentId != 0L
            && menuDomainService.getDescendantIdsIncludingSelf(tenantId, menuId).contains(newParentId)) {
            throw new BizException(AdminErrorCode.MENU_PARENT_INVALID.getCode(),
                AdminErrorCode.MENU_PARENT_INVALID.getMessage());
        }
    }

    /**
     * 空白字符串规范化为 null（用户决策 2026-08-22）：
     * 空串入库会命中部分唯一索引并被读链路误判为业务菜单，可选字段统一按 null 语义存储。
     */
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Long operatorId() {
        Long id = AccessRequestContext.getOperatorId();
        return id != null ? id : StpUtil.getLoginIdAsLong();
    }
}
