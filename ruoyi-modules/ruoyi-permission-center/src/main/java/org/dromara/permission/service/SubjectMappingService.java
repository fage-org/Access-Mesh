package org.dromara.permission.service;

import org.dromara.permission.domain.PcAbstractUser;

/**
 * 主体映射服务
 *
 * 负责将系统用户/服务映射到 permission-center 的 abstract_user
 *
 * @author RuoYi-Cloud-Plus
 */
public interface SubjectMappingService {

    /**
     * 根据用户类型和外部ID查找或创建抽象用户
     *
     * @param tenantId   租户ID
     * @param userType   用户类型枚举值
     * @param externalId 外部业务系统唯一标识
     * @param name       显示名（创建时使用）
     * @return 抽象用户
     */
    PcAbstractUser findOrCreate(Long tenantId, Integer userType, String externalId, String name);

    /**
     * 根据用户类型和外部ID查找抽象用户
     *
     * @param tenantId   租户ID
     * @param userType   用户类型枚举值
     * @param externalId 外部业务系统唯一标识
     * @return 抽象用户，不存在返回 null
     */
    PcAbstractUser findByExternalId(Long tenantId, Integer userType, String externalId);

    /**
     * 根据ID查找抽象用户
     *
     * @param tenantId 租户ID
     * @param id       抽象用户ID
     * @return 抽象用户
     */
    PcAbstractUser findById(Long tenantId, Long id);

    /**
     * 获取用户类型枚举值
     *
     * @param userTypeCode 用户类型编码（如 "sys_user", "service"）
     * @return 用户类型枚举值
     */
    Integer getUserTypeValue(String userTypeCode);
}
