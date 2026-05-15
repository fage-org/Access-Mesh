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

/**
 * 菜单同步处理器实现类
 * <p>
 * 将admin-service的菜单同步到permission-center的resource_entity表。
 * 使用菜单的权限标识(permCode)作为资源编码。
 * </p>
 */
@Service
public class MenuSyncHandlerImpl implements MenuSyncHandler {

    private static final Logger log = LoggerFactory.getLogger(MenuSyncHandlerImpl.class);
    private static final String RESOURCE_TYPE_MENU = "MENU";

    private final PermissionFeignClient permissionFeignClient;

    /**
     * 构造函数
     *
     * @param permissionFeignClient 权限中心Feign客户端
     */
    public MenuSyncHandlerImpl(PermissionFeignClient permissionFeignClient) {
        this.permissionFeignClient = permissionFeignClient;
    }

    /**
     * 同步菜单到权限中心
     * <p>
     * 创建或更新resource_entity（资源类型MENU），返回permResourceId。
     * 如果菜单没有permCode则跳过同步。
     * </p>
     *
     * @param tenantId 租户ID
     * @param menu     菜单实体
     * @return permission-center的resource_entity.id，失败返回null
     */
    @Override
    public Long syncMenuToPermissionCenter(Long tenantId, SysMenu menu) {
        if (menu == null || menu.getId() == null) {
            return null;
        }

        String code = generateResourceCode(menu);
        if (code == null || code.isBlank()) {
            log.debug("菜单无权限标识，跳过同步: menuId={}, menuName={}",
                menu.getId(), menu.getName());
            return null;
        }

        // 如果已有permResourceId，更新；否则创建
        if (menu.getPermResourceId() != null) {
            return updateExistingResource(menu, code);
        } else {
            return createNewResource(tenantId, menu, code);
        }
    }

    /**
     * 创建新资源
     * <p>
     * 在权限中心创建新的菜单资源实体。
     * </p>
     *
     * @param tenantId 租户ID
     * @param menu     菜单实体
     * @param code     资源编码
     * @return 创建成功返回资源ID，失败返回null
     */
    private Long createNewResource(Long tenantId, SysMenu menu, String code) {
        ResourceCreateReq req = new ResourceCreateReq(
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
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            log.warn("同步菜单到权限中心失败: menuId={}, menuName={}",
                menu.getId(), menu.getName());
            return null;
        }

        Object idObj = result.getData().get("id");
        if (idObj != null) {
            Long permResourceId = Long.valueOf(idObj.toString());
            log.info("同步菜单到权限中心成功: menuId={}, permResourceId={}",
                menu.getId(), permResourceId);
            return permResourceId;
        }
        return null;
    }

    /**
     * 更新已有资源
     * <p>
     * 在权限中心更新已存在的菜单资源实体。
     * </p>
     *
     * @param menu 菜单实体
     * @param code 资源编码
     * @return 更新成功返回资源ID，失败返回null
     */
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
        if (result == null || result.getCode() != 200) {
            log.warn("更新权限中心菜单失败: menuId={}, permResourceId={}",
                menu.getId(), menu.getPermResourceId());
            return null;
        }

        log.info("更新权限中心菜单成功: menuId={}, permResourceId={}",
            menu.getId(), menu.getPermResourceId());
        return menu.getPermResourceId();
    }

    /**
     * 批量同步菜单到权限中心
     *
     * @param tenantId 租户ID
     * @param menus    菜单列表
     * @return 同步成功数量
     */
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

    /**
     * 从权限中心删除菜单资源
     * <p>
     * 通过Feign调用权限中心删除指定的资源实体。
     * </p>
     *
     * @param tenantId       租户ID
     * @param permResourceId 权限中心的资源ID
     * @return 是否成功
     */
    @Override
    public boolean deleteMenuFromPermissionCenter(Long tenantId, Long permResourceId) {
        if (permResourceId == null) {
            return true; // 未同步过，视为成功
        }

        IdsReq req = new IdsReq(List.of(permResourceId));
        PermResult<Void> result = permissionFeignClient.deleteResources(req);
        if (result == null || result.getCode() != 200) {
            log.warn("从权限中心删除菜单失败: permResourceId={}", permResourceId);
            return false;
        }

        log.info("从权限中心删除菜单成功: permResourceId={}", permResourceId);
        return true;
    }

    /**
     * 生成菜单资源编码
     * <p>
     * 使用权限标识permCode作为资源编码。
     * </p>
     *
     * @param menu 菜单实体
     * @return 资源编码
     */
    @Override
    public String generateResourceCode(SysMenu menu) {
        return menu.getPermCode();
    }

    /**
     * 构建菜单扩展信息JSON
     * <p>
     * 将菜单的额外信息打包为JSON字符串格式。
     * </p>
     *
     * @param menu 菜单实体
     * @return JSON字符串
     */
    private String buildMenuExtra(SysMenu menu) {
        return String.format("{\"menuType\":\"%s\",\"component\":\"%s\",\"icon\":\"%s\",\"visible\":%s,\"serviceCode\":\"%s\"}",
            menu.getMenuType() != null ? menu.getMenuType() : "",
            menu.getComponent() != null ? menu.getComponent() : "",
            menu.getIcon() != null ? menu.getIcon() : "",
            menu.getVisible() != null ? menu.getVisible().toString() : "true",
            menu.getServiceCode() != null ? menu.getServiceCode() : ""
        );
    }
}