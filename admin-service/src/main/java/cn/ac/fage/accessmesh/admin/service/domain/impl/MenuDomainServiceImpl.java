package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper.DescendantResult;
import cn.ac.fage.accessmesh.admin.service.domain.MenuDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.ac.fage.accessmesh.admin.entity.table.SysMenuTableDef;

@Service
public class MenuDomainServiceImpl implements MenuDomainService {

    private final SysMenuMapper menuMapper;

    public MenuDomainServiceImpl(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    @Override
    public List<Long> getDescendantIds(Long tenantId, Long menuId) {
        if (menuId == null) {
            return List.of();
        }
        List<Long> ids = menuMapper.selectDescendantIds(tenantId, menuId);
        return ids != null ? ids : List.of();
    }

    @Override
    public List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long menuId) {
        if (menuId == null) {
            return List.of();
        }
        List<Long> ids = menuMapper.selectDescendantIdsIncludingSelf(tenantId, menuId);
        return ids != null ? ids : List.of();
    }

    @Override
    public Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Initialize result with empty lists for each input ID
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long id : menuIds) {
            result.put(id, new ArrayList<>());
        }

        // Performance fix: Use single batch CTE query instead of N+1 queries
        List<DescendantResult> descendants = menuMapper.selectBatchDescendantIds(tenantId, menuIds);
        for (DescendantResult dr : descendants) {
            Long rootId = dr.getMenuId();
            Long descId = dr.getDescendantId();
            if (rootId != null && descId != null) {
                result.computeIfAbsent(rootId, k -> new ArrayList<>()).add(descId);
            }
        }

        return result;
    }

    @Override
    public List<Long> getAncestorIds(Long tenantId, Long menuId) {
        // Performance fix: Use batch loading pattern (same as OrgDomainServiceImpl.batchGetAncestorIds)
        Map<Long, List<Long>> ancestorMap = batchGetAncestorIds(tenantId, Set.of(menuId));
        return ancestorMap.getOrDefault(menuId, List.of());
    }

    /**
     * Batch get ancestor IDs for multiple menu IDs.
     * Uses batch loading pattern to avoid N+1 queries.
     */
    public Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Batch load all menus and their ancestors
        Map<Long, SysMenu> entityMap = new HashMap<>();
        Set<Long> toLoad = new HashSet<>(menuIds);

        while (!toLoad.isEmpty()) {
            List<SysMenu> loaded = menuMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(SysMenuTableDef.SYS_MENU.TENANT_ID.eq(tenantId))
                    .and(SysMenuTableDef.SYS_MENU.ID.in(toLoad))
                    .and(SysMenuTableDef.SYS_MENU.DELETE_FLAG.eq(0))
            );
            toLoad.clear();
            for (SysMenu menu : loaded) {
                entityMap.put(menu.getId(), menu);
                if (menu.getParentId() != null && menu.getParentId() != 0L
                    && !entityMap.containsKey(menu.getParentId())) {
                    toLoad.add(menu.getParentId());
                }
            }
        }

        // Build ancestor chains for each input menuId
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long menuId : menuIds) {
            List<Long> ancestors = new ArrayList<>();
            Long current = menuId;
            while (current != null) {
                SysMenu menu = entityMap.get(current);
                if (menu == null) {
                    break;
                }
                if (menu.getParentId() != null && menu.getParentId() != 0L) {
                    ancestors.add(menu.getParentId());
                    current = menu.getParentId();
                } else {
                    break;
                }
            }
            result.put(menuId, ancestors);
        }
        return result;
    }

    @Override
    public SysMenu selectValidById(Long tenantId, Long menuId) {
        if (menuId == null) {
            return null;
        }
        return menuMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysMenuTableDef.SYS_MENU.ID.eq(menuId))
                .and(SysMenuTableDef.SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SysMenuTableDef.SYS_MENU.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public List<SysMenu> selectValidByIds(Long tenantId, Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysMenuTableDef.SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SysMenuTableDef.SYS_MENU.ID.in(menuIds))
                .and(SysMenuTableDef.SYS_MENU.DELETE_FLAG.eq(0))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteBatch(Long tenantId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        menuMapper.softDeleteBatch(tenantId, menuIds, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithChildren(Long tenantId, Long menuId) {
        SysMenu menu = selectValidById(tenantId, menuId);
        if (menu == null) return;

        List<Long> allIds = menuMapper.selectDescendantIdsIncludingSelf(tenantId, menuId);
        menuMapper.softDeleteBatch(tenantId, allIds, LocalDateTime.now());
    }

    @Override
    public boolean hasChildren(Long tenantId, Long menuId) {
        long count = menuMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(SysMenuTableDef.SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SysMenuTableDef.SYS_MENU.PARENT_ID.eq(menuId))
                .and(SysMenuTableDef.SYS_MENU.DELETE_FLAG.eq(0))
        );
        return count > 0;
    }

    @Override
    public SysMenu findByPermCode(Long tenantId, String permCode) {
        if (permCode == null || permCode.isBlank()) {
            return null;
        }
        return menuMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysMenuTableDef.SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SysMenuTableDef.SYS_MENU.PERM_CODE.eq(permCode))
                .and(SysMenuTableDef.SYS_MENU.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public List<SysMenu> findByPermCodes(Long tenantId, Set<String> permCodes) {
        if (permCodes == null || permCodes.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysMenuTableDef.SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SysMenuTableDef.SYS_MENU.PERM_CODE.in(permCodes))
                .and(SysMenuTableDef.SYS_MENU.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public int calculateDepth(Long tenantId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return 1;
        }

        // Performance fix: Pre-load ancestors and compute depth
        // Use batchGetAncestorIds to load all ancestors in batch
        Map<Long, List<Long>> ancestorMap = batchGetAncestorIds(tenantId, Set.of(parentId));
        List<Long> ancestors = ancestorMap.getOrDefault(parentId, List.of());

        // Depth = number of ancestors + 1 (for self)
        // ancestors includes all parent IDs up the tree
        return ancestors.size() + 1;
    }
}