package org.dromara.permission.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.permission.domain.PcTypeDefinition;

import java.util.List;

@Mapper
public interface PcTypeDefinitionMapper extends BaseMapperPlus<PcTypeDefinition, PcTypeDefinition> {

    List<PcTypeDefinition> selectByTenantAndTypeKey(@Param("tenantId") Long tenantId,
                                                    @Param("typeKey") String typeKey);

    PcTypeDefinition selectByTenantAndTypeKeyAndValue(@Param("tenantId") Long tenantId,
                                                      @Param("typeKey") String typeKey,
                                                      @Param("typeValue") Integer typeValue);
}
