package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysAuditLog;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统审计日志数据访问接口
 * <p>
 * 提供审计日志表的基础CRUD操作。
 * 审计日志记录用户的操作行为，用于系统审计和安全分析。
 * </p>
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

    /**
     * 分页查询指定租户的审计日志，按创建时间倒序排列
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<SysAuditLog> paginateByTenantId(@Param("page") Page<SysAuditLog> page,
                                         @Param("tenantId") Long tenantId);
}