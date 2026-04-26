package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.AbstractUser;

public interface AbstractUserDomainService {

    AbstractUser createUser(Integer userType, String externalId, String name, Boolean enabled, String extra, Long tenantId);

    void deleteUser(Long tenantId, Long userId);

    void enableUser(Long tenantId, Long userId);

    void disableUser(Long tenantId, Long userId);

    AbstractUser findByExternalId(Long tenantId, Integer userType, String externalId);
}
