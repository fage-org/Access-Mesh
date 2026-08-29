package cn.ac.fage.accessmesh.access.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 域配置数据访问接口
 * <p>
 * 提供域配置表的基础CRUD操作和自定义查询方法。
 * 域配置存储各业务域的特定配置项，如权限策略、同步规则等。
 * 支持批量软删除操作。
 * </p>
 */
public interface DomainConfigMapper extends BaseMapper<DomainConfig> {

    /**
     * 批量软删除域配置
     *
     * @param tenantId  租户ID
     * @param ids       待删除的域配置ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据租户ID、业务域ID和配置类型字符串查询有效域配置
     *
     * @param tenantId    租户ID
     * @param bizDomainId 业务域ID
     * @param configType  配置类型字符串
     * @return 域配置实体，不存在返回null
     */
    DomainConfig selectValidByTypeString(@Param("tenantId") Long tenantId,
                                          @Param("bizDomainId") Long bizDomainId,
                                          @Param("configType") String configType);

    /**
     * 根据租户ID查询所有有效域配置列表
     *
     * @param tenantId 租户ID
     * @return 域配置列表
     */
    List<DomainConfig> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据租户ID和业务域ID查询有效域配置列表
     *
     * @param tenantId    租户ID
     * @param bizDomainId 业务域ID
     * @return 域配置列表
     */
    List<DomainConfig> selectByTenantAndDomainId(@Param("tenantId") Long tenantId,
                                                   @Param("bizDomainId") Long bizDomainId);

    /**
     * 根据租户ID和ID集合查询有效域配置列表
     *
     * @param tenantId 租户ID
     * @param ids      域配置ID集合
     * @return 域配置列表
     */
    List<DomainConfig> selectValidByIds(@Param("tenantId") Long tenantId,
                                         @Param("ids") java.util.Set<Long> ids);

    /**
     * 批量查询业务域下是否存在有效域配置（业务域删除前引用检查，一次批量查询）
     *
     * @param tenantId    租户ID
     * @param domainIds   业务域ID集合
     * @return 存在有效配置的域配置列表（非空即存在引用）
     */
    List<DomainConfig> selectValidByDomainIds(@Param("tenantId") Long tenantId,
                                               @Param("domainIds") java.util.Set<Long> domainIds);
}