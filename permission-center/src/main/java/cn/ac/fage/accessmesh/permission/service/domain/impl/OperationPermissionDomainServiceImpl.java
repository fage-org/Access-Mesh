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
     * 根据ID查询操作权限
     * <p>
     * 直接调用Mapper的selectOneById方法。
     * 不检查租户和删除标志，用于内部查询。
     * </p>
     *
     * @param id 操作权限ID
     * @return 操作权限实体，不存在返回null
     */
    @Override
    public OperationPermission selectOneById(Long id) {
        return operationPermissionMapper.selectOneById(id);
    }

    /**
     * 插入操作权限
     * <p>
     * 调用Mapper的insert方法，插入新操作权限记录。
     * </p>
     *
     * @param entity 操作权限实体
     */
    @Override
    public void insert(OperationPermission entity) {
        operationPermissionMapper.insert(entity);
    }

    /**
     * 更新操作权限
     * <p>
     * 调用Mapper的update方法，更新已有操作权限记录。
     * </p>
     *
     * @param entity 操作权限实体
     * @return 更新影响的行数
     */
    @Override
    public int update(OperationPermission entity) {
        return operationPermissionMapper.update(entity);
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

    /**
     * 根据租户ID、资源类型集合和操作码集合查询操作权限列表
     *
     * @param tenantId         租户ID
     * @param resourceTypeValues 资源类型值集合
     * @param operationCodes   操作码集合
     * @return 操作权限列表
     */
    @Override
    public List<OperationPermission> selectByTenantResourceTypesAndOpCodes(Long tenantId,
                                                                            Set<Integer> resourceTypeValues,
                                                                            Set<String> operationCodes) {
        return operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(tenantId, resourceTypeValues, operationCodes);
    }

    /**
     * 根据租户ID查询操作权限列表（可选资源类型过滤）
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值，可为null
     * @return 操作权限列表
     */
    @Override
    public List<OperationPermission> selectByTenantAndResourceType(Long tenantId, Integer resourceType) {
        return operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceType);
    }
}