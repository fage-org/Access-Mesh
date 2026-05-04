package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.service.domain.MenuSyncHandler;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.IdsReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MenuSyncHandlerImpl implements MenuSyncHandler {

    private static final Logger log = LoggerFactory.getLogger(MenuSyncHandlerImpl.class);
    private static final String RESOURCE_TYPE_MENU = "MENU";

    private final PermissionFeignClient permissionFeignClient;

    public MenuSyncHandlerImpl(PermissionFeignClient permissionFeignClient) {
        this.permissionFeignClient = permissionFeignClient;
    }

    @Override
    public Long syncMenuToPermissionCenter(Long tenantId, SysMenu menu) {
        if (menu == null || menu.getId() == null) {
            return null;
        }

        String code = generateResourceCode(menu);
        if (code == null || code.isBlank()) {
            log.debug("Menu has no permCode, skip sync: menuId={}, menuName={}",
                menu.getId(), menu.getName());
            return null;
        }

        // 如果已有 permResourceId，更新；否则创建
        if (menu.getPermResourceId() != null) {
            return updateExistingResource(menu, code);
        } else {
            return createNewResource(tenantId, menu, code);
        }
    }

    private Long createNewResource(Long tenantId, SysMenu menu, String code) {
        ResourceCreateReq req = new ResourceCreateReq(
            null, // bizDomainId
            menu.getParentId() != null && menu.getParentId() != 0L ? menu.getParentId() : 0L,
            RESOURCE_TYPE_MENU,
            code,
            null, // codeType
            menu.getName(),
            menu.getPath(),
            menu.getStatus() != null ? menu.getStatus() : 1,
            menu.getSortOrder() != null ? menu.getSortOrder() : 0,
            buildMenuExtra(menu)
        );

        PermResult<Map<String, Object>> result = permissionFeignClient.createResource(req);
        if (result == null || result.code() != 200 || result.data() == null) {
            log.warn("Failed to sync menu to permission-center: menuId={}, menuName={}",
                menu.getId(), menu.getName());
            return null;
        }

        Object idObj = result.data().get("id");
        if (idObj != null) {
            Long permResourceId = Long.valueOf(idObj.toString());
            log.info("Synced menu to permission-center: menuId={}, permResourceId={}",
                menu.getId(), permResourceId);
            return permResourceId;
        }
        return null;
    }

    private Long updateExistingResource(SysMenu menu, String code) {
        ResourceUpdateReq req = new ResourceUpdateReq(
            menu.getPermResourceId(),
            code,
            menu.getName(),
            menu.getPath(),
            menu.getStatus(),
            menu.getSortOrder(),
            buildMenuExtra(menu)
        );

        PermResult<Map<String, Object>> result = permissionFeignClient.updateResource(req);
        if (result == null || result.code() != 200) {
            log.warn("Failed to update menu in permission-center: menuId={}, permResourceId={}",
                menu.getId(), menu.getPermResourceId());
            return null;
        }

        log.info("Updated menu in permission-center: menuId={}, permResourceId={}",
            menu.getId(), menu.getPermResourceId());
        return menu.getPermResourceId();
    }

    @Override
    public int batchSyncMenus(Long tenantId, Iterable<SysMenu> menus) {
        int successCount = 0;
        for (SysMenu menu : menus) {
            Long permResourceId = syncMenuToPermissionCenter(tenantId, menu);
            if (permResourceId != null) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public boolean deleteMenuFromPermissionCenter(Long tenantId, Long permResourceId) {
        if (permResourceId == null) {
            return true; // 未同步过，视为成功
        }

        IdsReq req = new IdsReq(List.of(permResourceId));
        PermResult<Void> result = permissionFeignClient.deleteResources(req);
        if (result == null || result.code() != 200) {
            log.warn("Failed to delete menu from permission-center: permResourceId={}", permResourceId);
            return false;
        }

        log.info("Deleted menu from permission-center: permResourceId={}", permResourceId);
        return true;
    }

    @Override
    public String generateResourceCode(SysMenu menu) {
        // 使用权限标识 permCode 作为资源编码
        return menu.getPermCode();
    }

    private String buildMenuExtra(SysMenu menu) {
        // 将额外信息打包为 JSON 字符串
        return String.format("{\"menuType\":\"%s\",\"component\":\"%s\",\"icon\":\"%s\",\"visible\":%s,\"serviceCode\":\"%s\"}",
            menu.getMenuType() != null ? menu.getMenuType() : "",
            menu.getComponent() != null ? menu.getComponent() : "",
            menu.getIcon() != null ? menu.getIcon() : "",
            menu.getVisible() != null ? menu.getVisible().toString() : "true",
            menu.getServiceCode() != null ? menu.getServiceCode() : ""
        );
    }
}