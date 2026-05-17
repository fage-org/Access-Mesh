package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.SystemConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置数据访问接口
 * <p>
 * 提供系统配置表的基础CRUD操作和自定义查询方法。
 * 系统配置表存储全局性的配置项，如缓存策略、权限判定规则等。
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
}