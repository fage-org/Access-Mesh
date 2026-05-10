package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth2客户端数据访问接口
 * <p>
 * 提供OAuth2客户端表的基础CRUD操作和自定义查询方法。
 * OAuth2客户端用于第三方应用接入认证。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysOauth2ClientMapper extends BaseMapper<SysOauth2Client> {

    /**
     * 批量软删除OAuth2客户端
     * <p>
     * 将指定客户端的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param ids       待删除的客户端ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}