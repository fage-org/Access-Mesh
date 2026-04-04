package org.dromara.permission.service;

import org.dromara.permission.model.permission.ResolvedRole;

import java.util.List;

public interface RoleResolverService {

    List<ResolvedRole> resolve(Long tenantId, Long abstractUserId, Long bizDomainId);
}
