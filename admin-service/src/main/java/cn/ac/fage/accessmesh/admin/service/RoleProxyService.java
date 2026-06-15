package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.dto.resp.RoleListItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserRoleItemResp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色代理服务接口
 * <p>
 * 提供admin-service与permission-center之间的角色操作代理方法。
 * 用于在admin-service中创建和管理permission-center的角色，
 * 实现跨服务的角色权限管理功能。
 * 接口使用业务键（roleTypeCode + roleExternalId）标识角色，
 * 不暴露 permission-center 内部 ID。
 * </p>
 */
public interface RoleProxyService {

    /**
     * 查询功能角色列表
     * <p>
     * 从permission-center查询指定类型的角色，转换为前端展示格式。
     * 默认仅返回功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），排除 ORG 和 POSITION。
     * </p>
     *
     * @param roleTypeCodes 角色类型编码列表（可选，为空则返回功能角色）
     * @return 角色列表项
     */
    List<RoleListItemResp> listRoles(List<String> roleTypeCodes);

    /**
     * 为组织创建角色
     * <p>
     * 在permission-center中创建角色，并关联到指定组织。
     * 用于组织创建时自动创建对应的角色实体。
     * </p>
     *
     * @param roleName  角色名称
     * @param orgId     组织ID（admin-service中的组织实体ID）
     * @param tenantId  租户ID
     * @return 创建的角色ID（permission-center中的角色ID）
     */
    Long createRoleForOrg(String roleName, Long orgId, Long tenantId);

    /**
     * 向角色授予菜单权限
     * <p>
     * 将指定菜单资源的权限授予角色。
     * 菜单ID会被解析为permission-center中的资源ID。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID（permission-center中的角色ID）
     * @param menuId   菜单实体ID（将被解析为资源ID）
     * @param opCode   操作码（如"VIEW"、"MANAGE"等）
     */
    void grantMenuToRole(Long tenantId, Long roleId, Long menuId, String opCode);

    /**
     * 从角色撤销菜单权限
     * <p>
     * 撤销角色对指定菜单资源的权限。
     * 菜单ID会被解析为permission-center中的资源ID。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID（permission-center中的角色ID）
     * @param menuId   菜单实体ID（将被解析为资源ID）
     */
    void revokeMenuFromRole(Long tenantId, Long roleId, Long menuId);

    /**
     * 加载用户的角色和有效权限
     * <p>
     * 从permission-center加载用户的角色列表和有效权限列表。
     * 用于构建用户信息响应中的角色和权限数据。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息响应，包含角色和权限列表
     */
    UserInfoResp loadUserRolesAndPermissions(Long userId);

    /**
     * 查询用户角色列表（admin 代理 permission-center）。
     * <p>
     * 返回全类型角色（ORG/POSITION/BASIC_ROLE/GROUP_ROLE/PERSONAL），
     * 代理层补 relationOrgName 等显示字段。
     * 门禁：ADMIN_USER:VIEW@userId。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.1
     *
     * @param userId 用户 ID
     * @return 用户角色列表
     */
    List<UserRoleItemResp> listUserRoles(Long userId);

    /**
     * 为用户分配功能角色（admin 代理 permission-center）。
     * <p>
     * 前端传入业务键，代理层直接透传给 permission-center。
     * 对目标角色做实例级 ADMIN_ROLE:GRANT 权限校验。
     * 仅允许分配功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），ORG/POSITION 走 /user-org/*。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.2
     *
     * @param userId         用户 ID
     * @param roleTypeCode   角色类型码（如 BASIC_ROLE / GROUP_ROLE / PERSONAL）
     * @param roleExternalId 角色外部标识（permission-center 业务键）
     * @param validFrom      有效期起始（可选）
     * @param validTo        有效期截止（可选）
     */
    void assignRole(Long userId, String roleTypeCode, String roleExternalId,
                    LocalDateTime validFrom, LocalDateTime validTo);

    /**
     * 回收用户功能角色（admin 代理 permission-center）。
     * <p>
     * 前端传入业务键，代理层直接透传给 permission-center。
     * 对目标角色做实例级 ADMIN_ROLE:REVOKE 权限校验。
     * 仅允许回收功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），ORG/POSITION 走 /user-org/*。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.3
     *
     * @param userId         用户 ID
     * @param roleTypeCode   角色类型码
     * @param roleExternalId 角色外部标识（permission-center 业务键）
     */
    void revokeRole(Long userId, String roleTypeCode, String roleExternalId);
}
