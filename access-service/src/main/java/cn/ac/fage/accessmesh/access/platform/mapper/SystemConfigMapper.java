package cn.ac.fage.accessmesh.access.platform.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.platform.entity.SystemConfig;
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
     * 按条件统计有效系统配置数量
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（configKey/description LIKE，大小写敏感）
     * @return 有效行数
     */
    long countByCondition(@Param("tenantId") Long tenantId,
                          @Param("keyword") String keyword);

    /**
     * 按条件分页查询有效系统配置（ORDER BY config_key, id）
     *
     * @param tenantId 租户ID
     * @param keyword  关键字，可选（configKey/description LIKE，大小写敏感）
     * @param limit    每页条数
     * @param offset   偏移量
     * @return 系统配置列表
     */
    List<SystemConfig> selectPageByCondition(@Param("tenantId") Long tenantId,
                                              @Param("keyword") String keyword,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);
}
