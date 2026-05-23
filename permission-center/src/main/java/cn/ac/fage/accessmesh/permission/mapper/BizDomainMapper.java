package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 业务域数据访问接口
 * <p>
 * 提供业务域表的基础CRUD操作和自定义查询方法。
 * 业务域用于划分权限的作用范围，如不同产品线或部门。
 * 支持批量软删除操作。
 * </p>
 */
public interface BizDomainMapper extends BaseMapper<BizDomain> {

    /**
     * 根据租户和域编码查询有效业务域
     *
     * @param tenantId   租户ID
     * @param domainCode 域编码
     * @return 业务域实体
     */
    BizDomain selectByCode(@Param("tenantId") Long tenantId,
                           @Param("domainCode") String domainCode);

    /**
     * 批量根据域编码查询有效业务域
     *
     * @param tenantId    租户ID
     * @param domainCodes 域编码集合
     * @return 业务域列表
     */
    List<BizDomain> selectByCodes(@Param("tenantId") Long tenantId,
                                  @Param("domainCodes") Set<String> domainCodes);

    /**
     * 批量软删除业务域
     * <p>
     * 将指定业务域的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的业务域ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据ID集合批量查询有效业务域
     *
     * @param tenantId 租户ID
     * @param ids      业务域ID集合
     * @return 业务域列表
     */
    List<BizDomain> selectValidByIds(@Param("tenantId") Long tenantId,
                                      @Param("ids") Set<Long> ids);

    /**
     * 根据ID和租户ID查询有效业务域
     *
     * @param domainId 业务域ID
     * @param tenantId 租户ID
     * @return 业务域实体，不存在返回null
     */
    BizDomain selectValidById(@Param("domainId") Long domainId,
                               @Param("tenantId") Long tenantId);

    /**
     * 根据租户ID查询所有有效业务域列表
     *
     * @param tenantId 租户ID
     * @return 业务域列表
     */
    List<BizDomain> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 查询租户下所有非全局的有效业务域
     *
     * @param tenantId 租户ID
     * @return 非全局业务域列表
     */
    List<BizDomain> selectNonGlobalByTenant(@Param("tenantId") Long tenantId);

    /**
     * 查询租户的全局域
     *
     * @param tenantId 租户ID
     * @return 全局域实体，不存在返回null
     */
    BizDomain selectGlobalByTenant(@Param("tenantId") Long tenantId);
}