package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织树配置数据访问接口
 * <p>
 * 提供组织树配置表的基础CRUD操作和自定义查询方法。
 * 组织树配置定义组织层级结构和展示规则。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysOrgTreeConfigMapper extends BaseMapper<SysOrgTreeConfig> {

    /**
     * 批量软删除组织树配置
     * <p>
     * 将指定配置的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID（可选，全局配置可为null）
     * @param ids       待删除的配置ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}