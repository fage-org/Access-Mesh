package cn.ac.fage.accessmesh.access.admin.service.domain;

import cn.ac.fage.accessmesh.access.admin.entity.SysUser;

import java.util.List;
import java.util.Set;

/**
 * 用户领域服务接口
 * <p>
 * 封装用户实体的核心领域逻辑，提供批量查询、软删除、状态管理等操作。
 * 用于跨多个业务场景的用户数据操作，避免业务层直接操作 Mapper。
 * 所有方法均遵循租户隔离原则，确保多租户数据安全。
 * </p>
 */
public interface UserDomainService {

    /**
     * 查询有效的用户实体
     * <p>
     * 查询未删除且属于指定租户的用户记录。
     * 用于需要精确验证用户存在性的场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userId   用户ID
     * @return 用户实体，不存在或已删除返回 null
     */
    SysUser selectValidById(Long tenantId, Long userId);

    /**
     * 批量查询有效的用户实体
     * <p>
     * 批量查询未删除且属于指定租户的用户记录。
     * 用于批量加载用户信息避免 N+1 查询问题。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userIds  用户ID集合
     * @return 用户实体列表，不存在的ID会被忽略
     */
    List<SysUser> selectValidByIds(Long tenantId, Set<Long> userIds);

    /**
     * 批量软删除用户
     * <p>
     * 将用户的 delete_flag 设置为用户ID，deleted_at 设置为当前时间。
     * 用于批量删除场景，保留数据记录便于审计追溯。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userIds  待删除的用户ID列表
     */
    void softDeleteBatch(Long tenantId, List<Long> userIds);

    /**
     * 批量更新用户状态
     * <p>
     * 批量启用或禁用用户账号。
     * 用于批量状态管理场景，如批量启用新员工账号。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param userIds  待更新的用户ID列表
     * @param status   目标状态（1=启用，0=禁用）
     */
    void batchUpdateStatus(Long tenantId, List<Long> userIds, Integer status);

    /**
     * 根据用户名查询用户
     * <p>
     * 通过用户名查找用户，用于登录验证和唯一性检查。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param username 用户名
     * @return 用户实体，不存在返回 null
     */
    SysUser findByUsername(Long tenantId, String username);

    /**
     * 根据手机号查询用户
     * <p>
     * 通过手机号查找用户，用于短信登录和手机号验证。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param phone    手机号
     * @return 用户实体，不存在返回 null
     */
    SysUser findByPhone(Long tenantId, String phone);

    /**
     * 检查用户名是否已存在
     * <p>
     * 验证用户名的唯一性，用于用户注册时的前置校验。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param username 用户名
     * @return 用户名已存在返回 true，不存在返回 false
     */
    boolean existsByUsername(Long tenantId, String username);

    /**
     * 检查手机号是否已存在
     * <p>
     * 验证手机号的唯一性，用于用户注册时的前置校验。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param phone    手机号
     * @return 手机号已存在返回 true，不存在返回 false
     */
    boolean existsByPhone(Long tenantId, String phone);

    /**
     * 批量查询已存在的用户名
     * <p>
     * 从给定的用户名集合中筛选出已存在的用户名。
     * 用于批量创建用户时的唯一性批量校验。
     * </p>
     *
     * @param tenantId  租户ID，用于多租户隔离
     * @param usernames 用户名集合
     * @return 已存在的用户名集合
     */
    Set<String> findExistingUsernames(Long tenantId, Set<String> usernames);

    /**
     * 批量查询已存在的手机号
     * <p>
     * 从给定的手机号集合中筛选出已存在的手机号。
     * 用于批量创建用户时的唯一性批量校验。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param phones   手机号集合
     * @return 已存在的手机号集合
     */
    Set<String> findExistingPhones(Long tenantId, Set<String> phones);

    /**
     * 批量插入用户
     * <p>
     * 批量插入多条用户记录，用于用户批量导入场景。
     * </p>
     *
     * @param users 用户列表
     */
    void insertBatch(List<SysUser> users);
}