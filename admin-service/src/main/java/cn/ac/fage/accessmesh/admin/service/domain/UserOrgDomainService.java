package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;

import java.util.List;

/**
 * 用户组织关联领域服务接口
 * <p>
 * 封装用户与组织关联的核心领域逻辑，提供关联查询、管理、批量操作等。
 * 支持用户多组织归属和主组织设置。
 * 用于用户数据范围权限控制和组织归属管理。
 * 所有方法均遵循租户隔离原则，确保多租户数据安全。
 * </p>
 */
public interface UserOrgDomainService {

    /**
     * 查询用户的所有组织关联
     * <p>
     * 获取用户所属的所有组织关联记录，包括主组织和非主组织。
     * 用于用户组织列表查询和数据范围权限计算。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userId   用户ID
     * @return 用户组织关联列表
     */
    List<SysUserOrg> findByUserId(Long tenantId, Long userId);

    /**
     * 删除用户的所有组织关联
     * <p>
     * 删除用户与所有组织的关联记录。
     * 用于用户删除时清理关联数据。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userId   用户ID
     */
    void deleteByUserId(Long tenantId, Long userId);

    /**
     * 删除用户的指定组织关联
     * <p>
     * 删除用户与特定组织的关联记录。
     * 用于用户调离组织或组织归属变更场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userId   用户ID
     * @param orgId    组织ID
     */
    void deleteByUserIdAndOrgId(Long tenantId, Long userId, Long orgId);

    /**
     * 批量插入用户组织关联
     * <p>
     * 批量创建用户与组织的关联记录。
     * 用于用户批量分配组织或新员工入职场景。
     * </p>
     *
     * @param userOrgs 用户组织关联列表
     */
    void insertBatch(List<SysUserOrg> userOrgs);

    /**
     * 设置用户的主组织
     * <p>
     * 将指定组织设为用户的主组织，同时将其他组织设为非主组织。
     * 主组织用于默认数据范围和权限继承计算。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userId   用户ID
     * @param orgId    主组织ID
     */
    void setPrimaryOrg(Long tenantId, Long userId, Long orgId);

    /**
     * 获取用户的组织简要信息列表
     * <p>
     * 查询用户所属组织的简要信息，包括组织名称、类型等。
     * 用于用户详情页展示组织归属信息。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userId   用户ID
     * @return 组织简要信息列表
     */
    List<UserPageItemResp.OrgBrief> getUserOrgBriefs(Long tenantId, Long userId);
}