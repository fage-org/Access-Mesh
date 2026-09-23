package cn.ac.fage.accessmesh.access.role.mapper;

import cn.ac.fage.accessmesh.access.role.dto.projection.OrgBriefProjection;
import cn.ac.fage.accessmesh.access.role.dto.projection.UserRoleProjection;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 用户角色组合查询 Mapper（跨域只读，T-ACCESS-006）。
 * <p>
 * 读取 permission 域表（abstract_role、user_role）与 admin 域表（sys_org），
 * 返回投影而非领域实体；所有查询显式携带 tenant_id 条件。禁止定义或执行任何写 SQL。
 * </p>
 */
public interface UserRoleQueryMapper {

    /**
     * 查询用户在有效期窗口内的角色关系投影（user_role ⨝ abstract_role，target 与 relation 各一次 LEFT JOIN）。
     * <p>
     * 语义保持：仅 delete_flag + 有效期窗口过滤，无 status/owner 过滤；
     * target 角色投影缺失时保留关系行（角色字段为 null）。
     * </p>
     *
     * @param tenantId       租户 ID
     * @param abstractUserId 抽象用户 ID（投影主键）
     * @param now            当前时间（有效期窗口判定基准）
     * @return 用户角色关系投影列表
     */
    List<UserRoleProjection> selectUserRoleProjections(
        @Param("tenantId") Long tenantId,
        @Param("abstractUserId") Long abstractUserId,
        @Param("now") LocalDateTime now
    );

    /**
     * 批量查询组织简要信息（按组织 ID 批量，避免逐组织单查）。
     *
     * @param tenantId 租户 ID
     * @param orgIds   组织 ID 集合（空集合返回空列表）
     * @return 组织简要投影列表
     */
    List<OrgBriefProjection> selectOrgBriefsByIds(
        @Param("tenantId") Long tenantId,
        @Param("orgIds") Collection<Long> orgIds
    );
}
