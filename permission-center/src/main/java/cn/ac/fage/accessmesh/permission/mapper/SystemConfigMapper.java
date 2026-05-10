package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.SystemConfig;

/**
 * 系统配置数据访问接口
 * <p>
 * 提供系统配置表的基础CRUD操作。
 * 系统配置表存储全局性的配置项，如缓存策略、权限判定规则等。
 * 使用MyBatis-Flex BaseMapper提供的通用方法。
 * </p>
 */
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {}