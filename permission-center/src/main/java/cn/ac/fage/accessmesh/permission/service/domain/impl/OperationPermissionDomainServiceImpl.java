package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.OperationPermissionDomainService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 操作权限领域服务实现类
 * <p>
 * 提供操作权限（OperationPermission）的基础数据访问操作。
 * 操作权限定义了系统支持的各种操作类型，如查看、编辑、删除、管理等。
 * 每个操作权限包含二进制位用于位运算权限匹配，继承掩码用于权限继承计算。
 * 该服务封装Mapper调用，提供统一的领域层访问接口。
 * </p>
 */
@Service
public class OperationPermissionDomainServiceImpl implements OperationPermissionDomainService {

    private final OperationPermissionMapper operationPermissionMapper;

    /**
     * 构造函数注入依赖
     *
     * @param operationPermissionMapper 操作权限数据访问层
     */
    public OperationPermissionDomainServiceImpl(OperationPermissionMapper operationPermissionMapper) {
        this.operationPermissionMapper = operationPermissionMapper;
    }

    /**
     * 根据ID查询有效操作权限
     * <p>
     * 查询未删除的操作权限实体，包含租户校验。
     * 如果操作权限ID为null，直接返回null。
     * </p>
     *
     * @param tenantId   租户ID
     * @param operationId 操作权限ID
     * @return 操作权限实体，不存在或已删除返回null
     */
    @Override
    public OperationPermission selectValidById(Long tenantId, Long operationId) {
        if (operationId == null) {
            return null;
        }
        return operationPermissionMapper.selectValidById(tenantId, operationId);
    }
}