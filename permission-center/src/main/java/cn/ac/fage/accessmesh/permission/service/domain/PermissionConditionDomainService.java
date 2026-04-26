package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;

import java.util.List;
import java.util.Map;

public interface PermissionConditionDomainService {

    List<RolePermSnapshot.RolePermEntry> evaluate(Long tenantId, List<RolePermSnapshot.RolePermEntry> entries,
                                                   Map<String, Object> context);
}
