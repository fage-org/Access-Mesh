package cn.ac.fage.accessmesh.access.infrastructure.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统配置数据访问接口
 * <p>
 * 提供系统配置表的基础CRUD操作和自定义查询方法。
 * 系统配置表存储全局性的配置项，如缓存策略、权限判定规则等。
 * </p>
 * <p>
 * 归并（T-ACCESS-002）：sys_config（admin）并入 system_config，原 admin 侧
 * SysConfigMapper 的批量软删/分页/列表/单查方法合并到本接口。
 * </p>
 */
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    /**
     * 根据租户ID和配置键查询有效系统配置
     *
     * @param tenantId   租户ID
     * @param configKey  配置键
     * @return 系统配置实体，不存在返回null
     */
    SystemConfig selectByConfigKey(@Param("tenantId") Long tenantId,
                                    @Param("configKey") String configKey);

    /**
     * 根据租户ID查询所有有效系统配置列表
     *
     * @param tenantId 租户ID
     * @return 系统配置列表
     */
    List<SystemConfig> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 批量软删除配置
     *
     * @param tenantId  租户ID
     * @param ids       待删除的配置ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 分页查询租户下的配置列表
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<SystemConfig> selectPageByTenantId(Page<SystemConfig> page,
                                            @Param("tenantId") Long tenantId);

    /**
     * 根据ID列表和租户ID查询配置列表
     *
     * @param tenantId 租户ID
     * @param ids      配置ID列表
     * @return 配置列表
     */
    List<SystemConfig> selectListByIdsAndTenantId(@Param("tenantId") Long tenantId,
                                                  @Param("ids") List<Long> ids);

    /**
     * 根据ID和租户ID查询单条配置
     *
     * @param tenantId 租户ID
     * @param id       配置ID
     * @return 配置实体
     */
    SystemConfig selectOneByIdAndTenantId(@Param("tenantId") Long tenantId,
                                          @Param("id") Long id);
}
