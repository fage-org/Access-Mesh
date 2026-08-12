package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysLoginLog;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统登录日志数据访问接口
 * <p>
 * 提供登录日志表的基础CRUD操作。
 * 登录日志记录用户的登录行为，包括登录时间、IP、状态等。
 * </p>
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {

    /**
     * 分页查询指定租户的登录日志，按登录时间倒序排列
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<SysLoginLog> paginateByTenantId(@Param("page") Page<SysLoginLog> page,
                                         @Param("tenantId") Long tenantId);
}