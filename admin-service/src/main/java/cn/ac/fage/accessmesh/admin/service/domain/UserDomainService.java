package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysUser;

import java.util.List;
import java.util.Set;

/**
 * 用户领域服务
 * 封装用户批量操作、软删除等核心领域逻辑
 */
public interface UserDomainService {

    /**
     * 查询有效的用户（未删除、属于指定租户）
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户实体，不存在返回null
     */
    SysUser selectValidById(Long tenantId, Long userId);

    /**
     * 批量查询有效的用户
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return 用户实体列表
     */
    List<SysUser> selectValidByIds(Long tenantId, Set<Long> userIds);

    /**
     * 批量软删除用户
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID列表
     */
    void softDeleteBatch(Long tenantId, List<Long> userIds);

    /**
     * 批量更新用户状态
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID列表
     * @param status   目标状态（1=启用，0=禁用）
     */
    void batchUpdateStatus(Long tenantId, List<Long> userIds, Integer status);

    /**
     * 根据用户名查询用户
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 用户实体
     */
    SysUser findByUsername(Long tenantId, String username);

    /**
     * 根据手机号查询用户
     *
     * @param tenantId 租户ID
     * @param phone    手机号
     * @return 用户实体
     */
    SysUser findByPhone(Long tenantId, String phone);

    /**
     * 检查用户名是否已存在
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(Long tenantId, String username);

    /**
     * 检查手机号是否已存在
     *
     * @param tenantId 租户ID
     * @param phone    手机号
     * @return 是否存在
     */
    boolean existsByPhone(Long tenantId, String phone);
}