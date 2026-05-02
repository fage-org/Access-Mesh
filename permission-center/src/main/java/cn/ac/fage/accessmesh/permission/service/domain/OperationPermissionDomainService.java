package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

public interface OperationPermissionDomainService {

    OperationPermission selectOneById(Long id);

    List<OperationPermission> selectListByQuery(QueryWrapper qw);

    OperationPermission selectOneByQuery(QueryWrapper qw);

    long selectCountByQuery(QueryWrapper qw);

    void insert(OperationPermission entity);

    int update(OperationPermission entity);

    /**
     * Select a valid operation permission by ID with tenant and delete flag conditions.
     * Returns null if operation not found, deleted, or doesn't belong to the tenant.
     */
    OperationPermission selectValidById(Long tenantId, Long operationId);
}
