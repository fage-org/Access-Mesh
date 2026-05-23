package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractUserDomainService;
import org.springframework.stereotype.Service;

/**
 * 抽象用户领域服务实现类
 * <p>
 * 提供抽象用户（AbstractUser）的CRUD操作。
 * 抽象用户是权限系统的统一用户模型，支持多种用户类型
 * （如系统用户、外部用户、服务账号等）。
 * 所有写操作均使用事务保证数据一致性。
 * 软删除通过设置deleteFlag实现，保留历史数据可追溯。
 * </p>
 */
@Service
public class AbstractUserDomainServiceImpl implements AbstractUserDomainService {

    private final AbstractUserMapper abstractUserMapper;

    public AbstractUserDomainServiceImpl(AbstractUserMapper abstractUserMapper) {
        this.abstractUserMapper = abstractUserMapper;
    }

    @Override
    public AbstractUser selectValidById(Long tenantId, Long userId) {
        if (userId == null) {
            return null;
        }
        return abstractUserMapper.selectValidById(userId, tenantId);
    }
}