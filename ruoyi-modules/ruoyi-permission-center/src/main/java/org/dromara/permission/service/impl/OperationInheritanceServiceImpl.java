package org.dromara.permission.service.impl;

import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.service.OperationInheritanceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OperationInheritanceServiceImpl implements OperationInheritanceService {

    @Override
    public List<MatchedPermission> filterByInheritance(List<MatchedPermission> matchedPermissions,
                                                       PcOperationPermission targetOperation,
                                                       Map<Long, PcOperationPermission> grantedOperations) {
        if (matchedPermissions == null || matchedPermissions.isEmpty() || targetOperation == null || targetOperation.getBinaryBit() == null) {
            return new ArrayList<>();
        }
        long targetBit = targetOperation.getBinaryBit();
        List<MatchedPermission> result = new ArrayList<>();
        for (MatchedPermission permission : matchedPermissions) {
            PcOperationPermission granted = grantedOperations.get(permission.getOperationId());
            if (granted == null) {
                continue;
            }
            long effectiveMask = (granted.getBinaryBit() == null ? 0L : granted.getBinaryBit())
                | (granted.getInheritMask() == null ? 0L : granted.getInheritMask());
            if ((effectiveMask & targetBit) == targetBit) {
                result.add(permission);
            }
        }
        return result;
    }
}
