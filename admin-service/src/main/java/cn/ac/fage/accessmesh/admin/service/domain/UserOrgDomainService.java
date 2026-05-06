package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;

import java.util.List;
import java.util.Set;

/**
 * 用户组织关联领域服务
 * 封装用户组织关联查询和管理核心领域逻辑
 */
public interface UserOrgDomainService {

    /**
     * 查询用户的所有组织关联
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户组织关联列表
     */
    List<SysUserOrg> findByUserId(Long tenantId, Long userId);

    /**
     * 删除用户的所有组织关联
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    void deleteByUserId(Long tenantId, Long userId);

    /**
     * 删除用户的指定组织关联
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param orgId    组织ID
     */
    void deleteByUserIdAndOrgId(Long tenantId, Long userId, Long orgId);

    /**
     * 批量插入用户组织关联
     *
     * @param userOrgs 用户组织关联列表
     */
    void insertBatch(List<SysUserOrg> userOrgs);

    /**
     * 设置用户的主组织
     * 将指定组织设为主组织，其他组织设为非主组织
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param orgId    主组织ID
     */
    void setPrimaryOrg(Long tenantId, Long userId, Long orgId);

    /**
     * 获取用户的组织简要信息列表（包含组织名称、类型）
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 组织简要信息列表
     */
    List<UserPageItemResp.OrgBrief> getUserOrgBriefs(Long tenantId, Long userId);

    /**
     * 批量查询用户的组织关联
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return userId -> 用户组织关联列表的映射
     */
    java.util.Map<Long, List<SysUserOrg>> batchFindByUserIds(Long tenantId, Set<Long> userIds);
}