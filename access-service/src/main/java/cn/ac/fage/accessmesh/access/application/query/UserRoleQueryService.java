package cn.ac.fage.accessmesh.access.application.query;

import cn.ac.fage.accessmesh.access.admin.dto.resp.RoleListItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserRoleItemResp;

import java.util.List;

/**
 * 用户角色组合查询服务（跨域只读，T-ACCESS-006）。
 * <p>
 * 聚合 permission 域（abstract_role、user_role）与 admin 域（sys_org 岗位组织名）
 * 数据，供 /role/list、/user-role/list 使用。门禁经 AdminPermissionValidator；
 * 数据读取走 query 包专用 Mapper，只读事务执行，不产生任何写 SQL。
 * </p>
 */
public interface UserRoleQueryService {

    /**
     * 查询功能角色列表（/role/list）。
     * <p>
     * 默认仅返回功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），显式传入 ORG/POSITION 时拒绝；
     * 门禁 ADMIN_ROLE:VIEW。结果固定 {@code LIMIT 0,200}（评审 P2-3，用户决策「保持 + 文档声明上限」）：
     * 功能角色面向前端下拉，超出 200 条属配置异常，由组织治理收敛；调用方不得假定全量返回。
     * </p>
     *
     * @param roleTypeCodes 角色类型编码列表（可选，为空则返回功能角色）
     * @return 角色列表项（最多 200 条）
     */
    List<RoleListItemResp> listRoles(List<String> roleTypeCodes);

    /**
     * 查询用户角色列表（/user-role/list）。
     * <p>
     * 返回全类型角色（ORG/POSITION/BASIC_ROLE/GROUP_ROLE/PERSONAL），
     * POSITION 角色补充所属组织名；门禁 ADMIN_USER:VIEW@userId。
     * </p>
     *
     * @param userId 用户 ID
     * @return 用户角色列表
     */
    List<UserRoleItemResp> listUserRoles(Long userId);
}
