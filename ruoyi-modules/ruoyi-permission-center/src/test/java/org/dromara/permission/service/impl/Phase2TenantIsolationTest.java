package org.dromara.permission.service.impl;

import org.dromara.permission.domain.dto.DomainRelationSaveReq;
import org.dromara.permission.domain.dto.ConditionSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.ResourceDependencySaveReq;
import org.dromara.permission.domain.PcDomainRelationConfig;
import org.dromara.permission.domain.PcDomainScopeBinding;
import org.dromara.permission.domain.PcDomainScopeConfig;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.PcResourceDependency;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcDomainRelationConfigMapper;
import org.dromara.permission.mapper.PcDomainScopeBindingMapper;
import org.dromara.permission.mapper.PcDomainScopeConfigMapper;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class Phase2TenantIsolationTest {

    @Mock
    private PcDomainRelationConfigMapper domainRelationConfigMapper;
    @Mock
    private PcDomainScopeConfigMapper domainScopeConfigMapper;
    @Mock
    private PcDomainScopeBindingMapper domainScopeBindingMapper;
    @Mock
    private PcAbstractRoleMapper abstractRoleMapper;
    @Mock
    private PcResourceEntityMapper resourceEntityMapper;
    @Mock
    private PcOperationPermissionMapper operationPermissionMapper;
    @Mock
    private PcPermissionConditionMapper permissionConditionMapper;
    @Mock
    private PcResourceDependencyMapper resourceDependencyMapper;

    @InjectMocks
    private DomainRelationConfigServiceImpl domainRelationConfigService;
    @InjectMocks
    private DomainScopeConfigServiceImpl domainScopeConfigService;
    @InjectMocks
    private DomainScopeBindingServiceImpl domainScopeBindingService;
    @InjectMocks
    private PermissionConditionServiceImpl permissionConditionService;
    @InjectMocks
    private ResourceDependencyServiceImpl resourceDependencyService;

    @Test
    void domainRelationUpdate_mismatchedTenant_skipsUpdate() {
        when(domainRelationConfigMapper.selectOne(any())).thenReturn(null);

        DomainRelationSaveReq req = new DomainRelationSaveReq();
        DomainRelationSaveReq.DomainRelationItem item = new DomainRelationSaveReq.DomainRelationItem();
        item.setId(1L);
        item.setTenantId(2L);
        req.setItems(java.util.List.of(item));

        domainRelationConfigService.save(req);

        verify(domainRelationConfigMapper, never()).updateById(any(PcDomainRelationConfig.class));
    }

    @Test
    void domainScopeRemove_mismatchedTenant_skipsDelete() {
        when(domainScopeConfigMapper.selectOne(any())).thenReturn(null);

        IdsReq req = new IdsReq();
        req.setTenantId(2L);
        req.setIds(java.util.List.of(1L));

        domainScopeConfigService.remove(req);

        verify(domainScopeConfigMapper, never()).updateById(any(PcDomainScopeConfig.class));
    }

    @Test
    void domainBindingRemove_mismatchedTenant_skipsDelete() {
        when(domainScopeBindingMapper.selectOne(any())).thenReturn(null);

        IdsReq req = new IdsReq();
        req.setTenantId(2L);
        req.setIds(java.util.List.of(1L));

        domainScopeBindingService.remove(req);

        verify(domainScopeBindingMapper, never()).updateById(any(PcDomainScopeBinding.class));
    }

    @Test
    void permissionConditionUpdate_mismatchedTenant_returnsInvalidRequest() {
        when(permissionConditionMapper.selectOne(any())).thenReturn(null);

        ConditionSaveReq req = new ConditionSaveReq();
        req.setId(1L);
        req.setTenantId(2L);

        PermissionServiceException ex = assertThrows(PermissionServiceException.class,
            () -> permissionConditionService.save(req));

        assertEquals(PermissionErrorCode.INVALID_REQUEST, ex.getErrorCode());
        verify(permissionConditionMapper, never()).updateById(any(PcPermissionCondition.class));
    }

    @Test
    void resourceDependencyRemove_mismatchedTenant_skipsDelete() {
        when(resourceDependencyMapper.selectOne(any())).thenReturn(null);

        IdsReq req = new IdsReq();
        req.setTenantId(2L);
        req.setIds(java.util.List.of(1L));

        resourceDependencyService.remove(req);

        verify(resourceDependencyMapper, never()).updateById(any(PcResourceDependency.class));
    }

    @Test
    void resourceDependencyUpdate_mismatchedTenant_skipsUpdate() {
        when(resourceDependencyMapper.selectOne(any())).thenReturn(null);

        ResourceDependencySaveReq req = new ResourceDependencySaveReq();
        req.setId(1L);
        req.setTenantId(2L);
        req.setResourceEntityId(10L);
        req.setDependsOnResourceEntityId(11L);
        req.setRequiredOperationPermissionId(12L);

        resourceDependencyService.save(req);

        verify(resourceDependencyMapper, never()).updateById(any(PcResourceDependency.class));
    }
}
