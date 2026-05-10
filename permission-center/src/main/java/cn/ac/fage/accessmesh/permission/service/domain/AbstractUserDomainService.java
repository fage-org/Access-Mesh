package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.AbstractUser;

/**
 * 抽象用户领域服务接口
 * <p>
 * 提供抽象用户（AbstractUser）的CRUD操作。
 * 抽象用户是权限系统的统一用户模型，支持多种用户类型
 * （如系统用户、外部用户、服务账号等）。
 * </p>
 */
public interface AbstractUserDomainService {

    /**
     * 创建抽象用户
     * <p>
     * 在指定租户下创建新的抽象用户。
     * 用户类型由调用方指定，支持多种用户类型。
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
    AbstractUser createUser(Integer userType, String externalId, String name, Boolean enabled, String extra, Long tenantId);

    /**
     * 软删除用户
     * <p>
     * 通过设置deleteFlag实现软删除。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    void deleteUser(Long tenantId, Long userId);

    /**
     * 启用用户
     * <p>
     * 将用户状态设置为启用。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    void enableUser(Long tenantId, Long userId);

    /**
     * 禁用用户
     * <p>
     * 将用户状态设置为禁用。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    void disableUser(Long tenantId, Long userId);

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
    AbstractUser findByExternalId(Long tenantId, Integer userType, String externalId);

    /**
     * 根据ID查询有效用户
     * <p>
     * 查询未删除的用户实体，包含租户校验。
     * 如果用户不存在、已删除或不属于租户，返回null。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户实体，不存在或已删除返回null
     */
    AbstractUser selectValidById(Long tenantId, Long userId);
}