package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigManageServiceImplTest {

    @Mock private TypeDefinitionMapper typeDefinitionMapper;
    @Mock private BizDomainMapper bizDomainMapper;
    @Mock private DomainConfigMapper domainConfigMapper;
    @Mock private ServiceConfigMapper serviceConfigMapper;
    @Mock private SystemConfigMapper systemConfigMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private ResourceApiMappingMapper resourceApiMappingMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private OperationLogDomainService operationLogDomainService;

    private ConfigManageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConfigManageServiceImpl(
            typeDefinitionMapper, bizDomainMapper, domainConfigMapper, serviceConfigMapper, systemConfigMapper,
            resourceEntityMapper, resourceApiMappingMapper, typeResolutionService, operationLogDomainService
        );
    }

    @Test
    void shouldUseSavedBasePathWhenRequestBasePathBlank() {
        ServiceConfig config = new ServiceConfig();
        config.setServiceCode("admin-service");
        config.setBasePath("/admin");
        config.setDeleteFlag(0L);
        when(serviceConfigMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(config);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(1);
        when(resourceEntityMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(resourceApiMappingMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        when(resourceApiMappingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(resourceEntityMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        doAnswer(inv -> {
            ResourceEntity entity = inv.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(resourceEntityMapper).insert(any(ResourceEntity.class));

        ServiceConfigSyncReq req = new ServiceConfigSyncReq(
            "admin-service",
            "",
            "FULL",
            List.of(new ServiceConfigSyncReq.GroupItem(
                "user",
                "用户管理",
                List.of(new ServiceConfigSyncReq.ApiItem("查询用户", "POST", "/api/user/list", "ACCESS", "admin:user:list", null))
            ))
        );

        service.syncServiceInterfaces(1L, req, null);

        ArgumentCaptor<ResourceApiMapping> mappingCaptor = ArgumentCaptor.forClass(ResourceApiMapping.class);
        org.mockito.Mockito.verify(resourceApiMappingMapper).insert(mappingCaptor.capture());
        assertEquals("/admin/api/user/list", mappingCaptor.getValue().getPathPattern());
    }
}
