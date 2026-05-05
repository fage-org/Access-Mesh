package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.OperationPermissionDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;
import cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef;

@Service
public class OperationPermissionDomainServiceImpl implements OperationPermissionDomainService {

    private final OperationPermissionMapper operationPermissionMapper;

    public OperationPermissionDomainServiceImpl(OperationPermissionMapper operationPermissionMapper) {
        this.operationPermissionMapper = operationPermissionMapper;
    }

    @Override
    public OperationPermission selectOneById(Long id) {
        return operationPermissionMapper.selectOneById(id);
    }

    @Override
    public List<OperationPermission> selectListByQuery(QueryWrapper qw) {
        return operationPermissionMapper.selectListByQuery(qw);
    }

    @Override
    public OperationPermission selectOneByQuery(QueryWrapper qw) {
        return operationPermissionMapper.selectOneByQuery(qw);
    }

    @Override
    public long selectCountByQuery(QueryWrapper qw) {
        return operationPermissionMapper.selectCountByQuery(qw);
    }

    @Override
    public void insert(OperationPermission entity) {
        operationPermissionMapper.insert(entity);
    }

    @Override
    public int update(OperationPermission entity) {
        return operationPermissionMapper.update(entity);
    }

    @Override
    public OperationPermission selectValidById(Long tenantId, Long operationId) {
        if (operationId == null) {
            return null;
        }
        return operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OperationPermissionTableDef.OPERATION_PERMISSION.ID.eq(operationId))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );
    }
}