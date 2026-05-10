package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

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
}