package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.resource.dto.RequiredPermission;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.enums.ApiMappingSource;
import java.util.List;

/** 手工、同步和 bootstrap 的共同映射校验与保存；事务由入口声明。 */
public interface ApiMappingWriteDomainService {
    record Write(ResourceApiMapping mapping, RequiredPermission requiredPermission) {}

    void saveAll(Long tenantId, String serviceCode, ApiMappingSource source, List<Write> writes);
}
