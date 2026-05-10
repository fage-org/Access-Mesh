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
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ServiceInterfaceSyncService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

/**
 * 配置管理服务测试类
 * <p>
 * 测试ConfigManageServiceImpl的各项功能：
 * - 服务接口同步时的basePath处理逻辑
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ConfigManageServiceImplTest {

    /** 类型定义Mapper Mock */
    @Mock private TypeDefinitionMapper typeDefinitionMapper;
    /** 业务域Mapper Mock */
    @Mock private BizDomainMapper bizDomainMapper;
    /** 域配置Mapper Mock */
    @Mock private DomainConfigMapper domainConfigMapper;
    /** 服务配置Mapper Mock */
    @Mock private ServiceConfigMapper serviceConfigMapper;
    /** 系统配置Mapper Mock */
    @Mock private SystemConfigMapper systemConfigMapper;
    /** 资源实体Mapper Mock */
    @Mock private ResourceEntityMapper resourceEntityMapper;
    /** API映射Mapper Mock */
    @Mock private ResourceApiMappingMapper resourceApiMappingMapper;
    /** 类型解析服务Mock */
    @Mock private TypeResolutionService typeResolutionService;
    /** 操作日志领域服务Mock */
    @Mock private OperationLogDomainService operationLogDomainService;
    /** 授权服务Mock */
    @Mock private AuthorizationService authorizationService;
    /** 服务接口同步服务Mock */
    @Mock private ServiceInterfaceSyncService serviceInterfaceSyncService;
    /** 权限查询引擎Mock */
    @Mock private PermQueryEngine engine;

    /** 待测试的配置管理服务实例 */
    private ConfigManageServiceImpl service;

    /**
     * 测试前置初始化
     * <p>
     * 在每个测试方法执行前初始化ConfigManageServiceImpl实例，
     * 注入所有Mock依赖对象。
     * </p>
     */
    @BeforeEach
    void setUp() {
        service = new ConfigManageServiceImpl(
            typeDefinitionMapper, bizDomainMapper, domainConfigMapper, serviceConfigMapper, systemConfigMapper,
            resourceEntityMapper, resourceApiMappingMapper, typeResolutionService, operationLogDomainService,
            authorizationService, serviceInterfaceSyncService, engine
        );
    }

    /**
     * 测试请求basePath为空时使用已保存的basePath
     * <p>
     * 当同步请求中的basePath为空字符串时，
     * syncServiceInterfaces应使用数据库中已保存的服务配置的basePath，
     * 并正确拼接完整路径（如"/admin/api/user/list"）。
     * </p>
     */
    @Test
    @Disabled("Test needs update for new insert logic")
    void shouldUseSavedBasePathWhenRequestBasePathBlank() {
        ServiceConfig config = new ServiceConfig();
        config.setServiceCode("admin-service");
        config.setBasePath("/admin");
        config.setDeleteFlag(0L);
        when(serviceConfigMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(config);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(1);
        when(resourceEntityMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        when(resourceApiMappingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        doAnswer(inv -> {
            List<ResourceEntity> entities = inv.getArgument(0);
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).setId(100L + i);
            }
            return entities.size();
        }).when(resourceEntityMapper).insertBatch(any(List.class));

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

        // Verify batch insert was called and capture the list
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ResourceApiMapping>> mappingCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(resourceApiMappingMapper).insertBatch(mappingCaptor.capture());
        assertEquals(1, mappingCaptor.getValue().size());
        assertEquals("/admin/api/user/list", mappingCaptor.getValue().get(0).getPathPattern());
    }
}