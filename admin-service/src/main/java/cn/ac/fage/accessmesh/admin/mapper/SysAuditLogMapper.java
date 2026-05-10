package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统审计日志数据访问接口
 * <p>
 * 提供审计日志表的基础CRUD操作。
 * 审计日志记录用户的操作行为，用于系统审计和安全分析。
 * </p>
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}