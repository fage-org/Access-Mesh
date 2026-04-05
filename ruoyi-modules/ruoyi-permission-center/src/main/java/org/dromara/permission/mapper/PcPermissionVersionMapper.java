package org.dromara.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.permission.domain.PcPermissionVersion;

/**
 * 权限版本表 permission_version 数据层
 *
 * @author RuoYi-Cloud-Plus
 */
@Mapper
public interface PcPermissionVersionMapper extends BaseMapper<PcPermissionVersion> {

    /**
     * 查询租户当前最新权限版本
     */
    PcPermissionVersion selectLatestByTenant(@Param("tenantId") Long tenantId);

    /**
     * PostgreSQL 事务级租户版本锁，防止同租户并发递增冲突
     */
    void lockTenantVersion(@Param("tenantId") Long tenantId);
}
