package cn.ac.fage.accessmesh.access.role.service;

import cn.ac.fage.accessmesh.access.role.dto.resp.UserRoleItemResp;

import java.util.List;

/**
 * 用户角色组合查询服务（跨域只读，T-ACCESS-006）。
 * <p>
 * 聚合 permission 域（abstract_role、user_role）与 admin 域（sys_org 岗位组织名）
 * 数据，供 /user-role/view 使用。门禁经 AdminPermissionValidator；
 * 数据读取走 query 包专用 Mapper，只读事务执行，不产生任何写 SQL。
 * 功能角色候选查询（原 /role/list）已退役——迁 /abstract-role/list
 * keyword+分页（T-FE-058，2026-09-23）。
 * </p>
 */
public interface UserRoleQueryAppService {

    /**
     * 查询用户角色列表（/user-role/view）。
     * <p>
     * 返回全类型角色（ORG/POSITION/BASIC_ROLE/GROUP_ROLE/PERSONAL），
     * POSITION 角色补充所属组织名；门禁 USER:VIEW@userId。
     * </p>
     *
     * @param userId 用户 ID
     * @return 用户角色列表
     */
    List<UserRoleItemResp> listUserRoles(Long userId);
}
