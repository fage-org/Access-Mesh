package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourcePermissionStrategy;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Component;

import static cn.ac.fage.accessmesh.permission.entity.table.BizDomainTableDef.BIZ_DOMAIN;

/**
 * Strategy for DOMAIN resource ID conversion.
 * Converts bizDomainId to resource_entity.code via bizDomain.code lookup.
 */
@Component
public class DomainPermissionStrategy implements ResourcePermissionStrategy<Long> {

    private static final String DOMAIN_TYPE_CODE = "DOMAIN";

    private final BizDomainMapper bizDomainMapper;

    public DomainPermissionStrategy(BizDomainMapper bizDomainMapper) {
        this.bizDomainMapper = bizDomainMapper;
    }

    @Override
    public String getResourceTypeCode() {
        return DOMAIN_TYPE_CODE;
    }

    @Override
    public String toResourceEntityCode(Long tenantId, Long bizDomainId) {
        BizDomain domain = bizDomainMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(BIZ_DOMAIN.ID.eq(bizDomainId))
                .and(BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                .and(BIZ_DOMAIN.DELETE_FLAG.eq(0))
        );
        return domain != null ? domain.getCode() : null;
    }
}