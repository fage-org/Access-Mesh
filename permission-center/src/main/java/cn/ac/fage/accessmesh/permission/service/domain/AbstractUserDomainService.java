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