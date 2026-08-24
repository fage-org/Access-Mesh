package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.service.domain.OperationResolutionDomainService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作定义「专属优先、全局回退」解析实现（纯内存，见接口契约说明）。
 */
@Service
public class OperationResolutionDomainServiceImpl implements OperationResolutionDomainService {

    @Override
    public List<OperationPermission> mergeGlobalFallback(List<OperationPermission> operations, Integer resourceType) {
        List<OperationPermission> safe = operations == null ? List.of() : operations;
        if (resourceType == null) {
            return safe.stream()
                .filter(op -> op.getResourceType() == null)
                .collect(Collectors.toList());
        }
        List<OperationPermission> dedicated = safe.stream()
            .filter(op -> Objects.equals(op.getResourceType(), resourceType))
            .collect(Collectors.toList());
        Set<String> dedicatedCodes = dedicated.stream()
            .map(OperationPermission::getCode)
            .map(OperationResolutionDomainService::normalizeCode)
            .collect(Collectors.toSet());
        List<OperationPermission> merged = new ArrayList<>(dedicated);
        safe.stream()
            .filter(op -> op.getResourceType() == null)
            .filter(op -> !dedicatedCodes.contains(OperationResolutionDomainService.normalizeCode(op.getCode())))
            .forEach(merged::add);
        return merged;
    }
}
