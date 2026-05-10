package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.OperationLog;

/**
 * 操作日志数据访问接口
 * <p>
 * 提供操作日志表的基础CRUD操作。
 * 操作日志表记录用户的操作行为，用于审计和追踪。
 * 使用MyBatis-Flex BaseMapper提供的通用方法。
 * </p>
 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {}