package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户领域服务实现类
 * <p>
 * 封装用户数据访问的核心领域逻辑，包括单条/批量查询、存在性校验、批量操作等。
 * 所有查询均带有租户隔离和删除标记过滤，确保数据安全。
 * 提供批量操作方法优化性能，避免N+1查询问题。
 * </p>
 */
@Service
public class UserDomainServiceImpl implements UserDomainService {

    private final SysUserMapper userMapper;

    /**
     * 构造函数注入依赖
     *
     * @param userMapper 用户数据访问层
     */
    public UserDomainServiceImpl(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 查询有效的用户
     * <p>
     * 根据用户ID查询用户，带租户隔离和删除标记过滤。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userId   用户ID
     * @return 用户实体，不存在返回null
     */
    @Override
    public SysUser selectValidById(Long tenantId, Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectValidById(tenantId, userId);
    }

    /**
     * 批量查询有效的用户
     * <p>
     * 根据用户ID集合批量查询用户，带租户隔离和删除标记过滤。
     * 使用单次SQL查询，避免N+1问题。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userIds  用户ID集合
     * @return 用户实体列表
     */
    @Override
    public List<SysUser> selectValidByIds(Long tenantId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectValidByIds(tenantId, userIds);
    }

    /**
     * 批量软删除用户
     * <p>
     * 将指定用户标记为已删除（deleteFlag设置为本行id）。
     * 使用单条批量SQL优化性能。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userIds  用户ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteBatch(Long tenantId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userMapper.softDeleteBatch(tenantId, userIds, LocalDateTime.now());
    }

    /**
     * 批量更新用户状态
     * <p>
     * 将指定用户的状态批量更新（启用/禁用）。
     * 使用单条批量SQL优化性能。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param userIds  用户ID列表
     * @param status   新状态值（0禁用，1启用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateStatus(Long tenantId, List<Long> userIds, Integer status) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userMapper.batchUpdateStatus(tenantId, userIds, status, LocalDateTime.now());
    }

    /**
     * 根据用户名查询用户
     * <p>
     * 用于登录验证和用户名唯一性校验。
     * 带租户隔离和删除标记过滤。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param username 用户名
     * @return 用户实体，不存在返回null
     */
    @Override
    public SysUser findByUsername(Long tenantId, String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userMapper.selectByUsername(tenantId, username);
    }

    /**
     * 根据手机号查询用户
     * <p>
     * 用于短信登录验证和手机号唯一性校验。
     * 带租户隔离和删除标记过滤。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param phone    手机号
     * @return 用户实体，不存在返回null
     */
    @Override
    public SysUser findByPhone(Long tenantId, String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return userMapper.selectByPhone(tenantId, phone);
    }

    /**
     * 检查用户名是否存在
     * <p>
     * 用于创建用户时的用户名唯一性校验。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param username 用户名
     * @return 是否存在
     */
    @Override
    public boolean existsByUsername(Long tenantId, String username) {
        return findByUsername(tenantId, username) != null;
    }

    /**
     * 检查手机号是否存在
     * <p>
     * 用于创建用户时的手机号唯一性校验。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param phone    手机号
     * @return 是否存在
     */
    @Override
    public boolean existsByPhone(Long tenantId, String phone) {
        return findByPhone(tenantId, phone) != null;
    }

    /**
     * 批量查询已存在的用户名
     * <p>
     * 用于批量创建用户时的用户名唯一性校验。
     * 返回已存在的用户名集合，便于业务层判断冲突。
     * </p>
     *
     * @param tenantId  租户ID，用于租户隔离
     * @param usernames 用户名集合
     * @return 已存在的用户名集合
     */
    @Override
    public Set<String> findExistingUsernames(Long tenantId, Set<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Set.of();
        }
        List<SysUser> existingUsers = userMapper.selectExistingByUsernames(tenantId, usernames);
        return existingUsers.stream().map(SysUser::getUsername).collect(Collectors.toSet());
    }

    /**
     * 批量查询已存在的手机号
     * <p>
     * 用于批量创建用户时的手机号唯一性校验。
     * 返回已存在的手机号集合，便于业务层判断冲突。
     * 过滤null和空字符串后再查询。
     * </p>
     *
     * @param tenantId 租户ID，用于租户隔离
     * @param phones   手机号集合
     * @return 已存在的手机号集合
     */
    @Override
    public Set<String> findExistingPhones(Long tenantId, Set<String> phones) {
        if (phones == null || phones.isEmpty()) {
            return Set.of();
        }
        // 过滤 null 和空字符串
        Set<String> validPhones = phones.stream()
            .filter(p -> p != null && !p.isBlank())
            .collect(Collectors.toSet());
        if (validPhones.isEmpty()) {
            return Set.of();
        }
        List<SysUser> existingUsers = userMapper.selectExistingByPhones(tenantId, validPhones);
        return existingUsers.stream().map(SysUser::getPhone).collect(Collectors.toSet());
    }

    /**
     * 批量插入用户
     * <p>
     * 用于批量创建用户场景，使用单条批量SQL优化性能。
     * </p>
     *
     * @param users 用户实体列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertBatch(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        userMapper.insertBatch(users);
    }
}