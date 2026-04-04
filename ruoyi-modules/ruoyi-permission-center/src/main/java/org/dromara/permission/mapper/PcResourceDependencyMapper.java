package org.dromara.permission.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.permission.domain.PcResourceDependency;

import java.util.List;

/**
 * 资源依赖表 resource_dependency 数据层
 *
 * @author RuoYi-Cloud-Plus
 */
@Mapper
public interface PcResourceDependencyMapper extends BaseMapperPlus<PcResourceDependency, PcResourceDependency> {

    List<PcResourceDependency> selectByTenantAndResourceEntityId(@Param("tenantId") Long tenantId,
                                                                 @Param("resourceEntityId") Long resourceEntityId);
}
