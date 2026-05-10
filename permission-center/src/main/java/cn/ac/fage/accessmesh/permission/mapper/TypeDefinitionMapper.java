package cn.ac.fage.accessmesh.permission.mapper;

import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 类型定义数据访问接口
 * <p>
 * 提供类型定义表的基础CRUD操作和自定义查询方法。
 * 类型定义存储系统中各种枚举类型的定义，如资源类型、角色类型、操作类型等。
 * 支持批量软删除操作。
 * </p>
 */
public interface TypeDefinitionMapper extends BaseMapper<TypeDefinition> {

    /**
     * 批量软删除类型定义
     * <p>
     * 将指定类型定义的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的类型定义ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}