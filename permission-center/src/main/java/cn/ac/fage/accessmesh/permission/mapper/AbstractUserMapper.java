package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 抽象用户数据访问接口
 * <p>
 * 提供抽象用户表的基础CRUD操作和自定义查询方法。
 * 抽象用户包括内部用户和外部用户，是权限分配的主体。
 * 支持批量软删除操作。
 * </p>
 */
public interface AbstractUserMapper extends BaseMapper<AbstractUser> {

    /**
     * 批量软删除抽象用户
     * <p>
     * 将指定用户的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的抽象用户ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}