package org.dromara.permission.service;

import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.model.permission.MatchedPermission;

import java.util.List;
import java.util.Map;

public interface OperationInheritanceService {

    List<MatchedPermission> filterByInheritance(List<MatchedPermission> matchedPermissions,
                                                PcOperationPermission targetOperation,
                                                Map<Long, PcOperationPermission> grantedOperations);
}
