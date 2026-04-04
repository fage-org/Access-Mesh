package org.dromara.permission.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.permission.domain.PcPermissionConflictRule;

import java.util.List;

/**
 * 权限冲突规则表 permission_conflict_rule 数据层
 *
 * @author RuoYi-Cloud-Plus
 */
@Mapper
public interface PcPermissionConflictRuleMapper extends BaseMapperPlus<PcPermissionConflictRule, PcPermissionConflictRule> {

    List<PcPermissionConflictRule> selectByTenantAndResourceType(@Param("tenantId") Long tenantId,
                                                                 @Param("resourceTypeValue") Integer resourceTypeValue);
}
