package cn.ac.fage.accessmesh.access.application.query.mapper;

import cn.ac.fage.accessmesh.access.application.query.projection.MenuProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserOrgProjection;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 用户菜单聚合查询 Mapper（跨域只读，T-ACCESS-006）。
 * <p>
 * 只读 admin 域表（sys_menu、sys_user_org），返回投影而非领域实体；
 * 所有查询显式携带 tenant_id 条件。禁止定义或执行任何写 SQL。
 * </p>
 */
public interface UserMenuQueryMapper {

    /**
     * 查询租户全部有效菜单（仅 delete_flag 过滤，状态过滤由服务层构建菜单树时执行）。
     *
     * @param tenantId 租户 ID
     * @return 菜单投影列表，按 sort_order 升序
     */
    List<MenuProjection> selectMenus(@Param("tenantId") Long tenantId);

    /**
     * 批量查询用户-组织关系（按用户 ID 批量，避免逐用户单查）。
     *
     * @param tenantId 租户 ID
     * @param userIds  用户 ID 集合（空集合返回空列表）
     * @return 用户-组织关系投影列表
     */
    List<UserOrgProjection> selectUserOrgsByUserIds(
        @Param("tenantId") Long tenantId,
        @Param("userIds") Collection<Long> userIds
    );
}
