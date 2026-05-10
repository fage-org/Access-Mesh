package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.PermissionVersion;

/**
 * 权限版本数据访问接口
 * <p>
 * 提供权限版本表的基础CRUD操作。
 * 权限版本表用于缓存一致性检查，Gateway通过版本号判断缓存的权限快照是否过期。
 * 使用MyBatis-Flex BaseMapper提供的通用方法。
 * </p>
 */
public interface PermissionVersionMapper extends BaseMapper<PermissionVersion> {}