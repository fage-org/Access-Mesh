package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统登录日志数据访问接口
 * <p>
 * 提供登录日志表的基础CRUD操作。
 * 登录日志记录用户的登录行为，包括登录时间、IP、状态等。
 * </p>
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}