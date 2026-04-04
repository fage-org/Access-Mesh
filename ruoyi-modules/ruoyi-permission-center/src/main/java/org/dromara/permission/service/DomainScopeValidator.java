package org.dromara.permission.service;

import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcResourceEntity;

public interface DomainScopeValidator {

    void validateGrantScope(Long tenantId, Long bizDomainId, PcAbstractRole role, PcResourceEntity resource, PcOperationPermission operation);
}
