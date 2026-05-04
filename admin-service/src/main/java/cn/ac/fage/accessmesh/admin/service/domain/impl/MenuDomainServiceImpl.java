package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper;
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

import static cn.ac.fage.accessmesh.admin.entity.table.SysMenuTableDef.SYS_MENU;

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

        Map<Long, List<Long>> result = new HashMap<>();
        for (Long id : menuIds) {
            result.put(id, new ArrayList<>());
        }

        for (Long menuId : menuIds) {
            List<Long> descendants = menuMapper.selectDescendantIds(tenantId, menuId);
            if (descendants != null && !descendants.isEmpty()) {
                result.get(menuId).addAll(descendants);
            }
        }

        return result;
    }

    @Override
    public List<Long> getAncestorIds(Long tenantId, Long menuId) {
        List<Long> ids = new ArrayList<>();
        Long current = menuId;
        while (current != null) {
            SysMenu menu = menuMapper.selectOneById(current);
            if (menu == null || menu.getDeleteFlag() != 0L || !menu.getTenantId().equals(tenantId)) {
                break;
            }
            if (menu.getParentId() != null && menu.getParentId() != 0L) {
                ids.add(menu.getParentId());
                current = menu.getParentId();
            } else {
                break;
            }
        }
        return ids;
    }

    @Override
    public SysMenu selectValidById(Long tenantId, Long menuId) {
        if (menuId == null) {
            return null;
        }
        return menuMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SYS_MENU.ID.eq(menuId))
                .and(SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SYS_MENU.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public List<SysMenu> selectValidByIds(Long tenantId, Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SYS_MENU.ID.in(menuIds))
                .and(SYS_MENU.DELETE_FLAG.eq(0))
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
                .where(SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SYS_MENU.PARENT_ID.eq(menuId))
                .and(SYS_MENU.DELETE_FLAG.eq(0))
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
                .where(SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SYS_MENU.PERM_CODE.eq(permCode))
                .and(SYS_MENU.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public List<SysMenu> findByPermCodes(Long tenantId, Set<String> permCodes) {
        if (permCodes == null || permCodes.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_MENU.TENANT_ID.eq(tenantId))
                .and(SYS_MENU.PERM_CODE.in(permCodes))
                .and(SYS_MENU.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public int calculateDepth(Long tenantId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return 1;
        }
        int depth = 0;
        Long current = parentId;
        while (current != null && current != 0L) {
            SysMenu menu = menuMapper.selectOneById(current);
            if (menu == null || menu.getDeleteFlag() != 0L) {
                break;
            }
            depth++;
            current = menu.getParentId();
        }
        return depth + 1;
    }
}