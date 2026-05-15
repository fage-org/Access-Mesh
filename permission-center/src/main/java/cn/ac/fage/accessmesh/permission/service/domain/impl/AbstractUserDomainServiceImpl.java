package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.enums.UserType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractUserDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef;

/**
 * 抽象用户领域服务实现类
 * <p>
 * 提供抽象用户（AbstractUser）的CRUD操作。
 * 抽象用户是权限系统的统一用户模型，支持多种用户类型
 * （如系统用户、外部用户、服务账号等）。
 * 所有写操作均使用事务保证数据一致性。
 * 软删除通过设置deleteFlag实现，保留历史数据可追溯。
 * </p>
 */
@Service
public class AbstractUserDomainServiceImpl implements AbstractUserDomainService {

    private final AbstractUserMapper abstractUserMapper;

    /**
     * 构造函数注入依赖
     *
     * @param abstractUserMapper 抽象用户数据访问层
     */
    public AbstractUserDomainServiceImpl(AbstractUserMapper abstractUserMapper) {
        this.abstractUserMapper = abstractUserMapper;
    }

    /**
     * 创建抽象用户
     * <p>
     * 在指定租户下创建新的抽象用户。
     * 用户类型由调用方指定，支持系统用户、外部用户等多种类型。
     * 默认启用状态为true，如果未指定则使用默认值。
     * </p>
     *
     * @param userType   用户类型值（参考UserType枚举）
     * @param externalId 外部标识，用于与外部系统关联
     * @param name       用户名称
     * @param enabled    是否启用，可选
     * @param extra      扩展属性JSON，可选
     * @param tenantId   租户ID
     * @return 创建的用户实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbstractUser createUser(Integer userType, String externalId, String name, Boolean enabled, String extra, Long tenantId) {
        AbstractUser user = new AbstractUser();
        user.setTenantId(tenantId);
        user.setUserType(userType);
        user.setExternalId(externalId);
        user.setName(name);
        user.setEnabled(enabled != null ? enabled : true);
        user.setExtra(extra);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleteFlag(0L);
        abstractUserMapper.insert(user);
        return user;
    }

    /**
     * 软删除用户
     * <p>
     * 通过设置deleteFlag为用户ID实现软删除。
     * 软删除后的用户不再参与权限计算，但历史数据可追溯。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long tenantId, Long userId) {
        AbstractUser user = selectValidById(tenantId, userId);
        if (user != null) {
            user.setDeleteFlag(user.getId());
            user.setDeletedAt(LocalDateTime.now());
            abstractUserMapper.update(user);
        }
    }

    /**
     * 启用用户
     * <p>
     * 将用户状态设置为启用。
     * 启用后的用户可以正常登录和使用系统。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(Long tenantId, Long userId) {
        updateUserStatus(tenantId, userId, true);
    }

    /**
     * 禁用用户
     * <p>
     * 将用户状态设置为禁用。
     * 禁用后的用户无法登录和访问系统。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(Long tenantId, Long userId) {
        updateUserStatus(tenantId, userId, false);
    }

    /**
     * 根据外部标识查询用户
     * <p>
     * 在指定租户和用户类型下，根据外部标识查询用户。
     * 用于用户同步时查找已存在的用户记录。
     * </p>
     *
     * @param tenantId   租户ID
     * @param userType   用户类型值
     * @param externalId 外部标识
     * @return 用户实体，不存在返回null
     */
    @Override
    public AbstractUser findByExternalId(Long tenantId, Integer userType, String externalId) {
        return abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(AbstractUserTableDef.ABSTRACT_USER.USER_TYPE.eq(userType))
                .and(AbstractUserTableDef.ABSTRACT_USER.EXTERNAL_ID.eq(externalId))
                .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 更新用户启用状态
     * <p>
     * 内部方法，用于更新用户的enabled字段。
     * </p>
     *
     * @param userId  用户ID
     * @param enabled 启用状态
     */
    private void updateUserStatus(Long tenantId, Long userId, boolean enabled) {
        // Verify user belongs to tenant before updating
        AbstractUser existing = selectValidById(tenantId, userId);
        if (existing == null) {
            return;
        }
        existing.setEnabled(enabled);
        existing.setUpdatedAt(LocalDateTime.now());
        abstractUserMapper.update(existing);
    }

    /**
     * 根据ID查询有效用户
     * <p>
     * 查询未删除的用户实体。
     * 如果用户ID为null，直接返回null。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户实体，不存在或已删除返回null
     */
    @Override
    public AbstractUser selectValidById(Long tenantId, Long userId) {
        if (userId == null) {
            return null;
        }
        return abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractUserTableDef.ABSTRACT_USER.ID.eq(userId))
                .and(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
    }
}