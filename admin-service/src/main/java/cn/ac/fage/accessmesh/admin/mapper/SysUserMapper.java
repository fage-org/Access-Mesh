package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户数据访问接口
 * <p>
 * 提供用户表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除、批量更新状态、批量更新密码等操作。
 * </p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 批量软删除用户
     *
     * @param tenantId  租户ID
     * @param ids       用户ID列表
     * @param deletedAt 删除时间
     * @return 影响行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 批量更新用户状态
     *
     * @param tenantId  租户ID
     * @param ids       用户ID列表
     * @param status    目标状态
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int batchUpdateStatus(@Param("tenantId") Long tenantId,
                          @Param("ids") List<Long> ids,
                          @Param("status") Integer status,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 批量更新用户密码
     *
     * @param tenantId     租户ID
     * @param ids          用户ID列表
     * @param password     新密码（已加密）
     * @param updatedAt    更新时间
     * @return 影响行数
     */
    int batchUpdatePassword(@Param("tenantId") Long tenantId,
                            @Param("ids") List<Long> ids,
                            @Param("password") String password,
                            @Param("updatedAt") LocalDateTime updatedAt);
}
