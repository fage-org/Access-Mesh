package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractUser;
import org.dromara.permission.domain.PcTypeDefinition;
import org.dromara.permission.mapper.PcAbstractUserMapper;
import org.dromara.permission.mapper.PcTypeDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SubjectMappingServiceImplTest {

    @Mock
    private PcAbstractUserMapper abstractUserMapper;

    @Mock
    private PcTypeDefinitionMapper typeDefinitionMapper;

    @InjectMocks
    private SubjectMappingServiceImpl service;

    @Test
    void findByExternalId_returnsUserWhenExists() {
        PcAbstractUser user = new PcAbstractUser();
        user.setId(100L);
        user.setTenantId(1L);
        user.setUserType(1);
        user.setExternalId("user-123");

        when(abstractUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        PcAbstractUser result = service.findByExternalId(1L, 1, "user-123");

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("user-123", result.getExternalId());
    }

    @Test
    void findByExternalId_returnsNullWhenNotExists() {
        when(abstractUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        PcAbstractUser result = service.findByExternalId(1L, 1, "nonexistent");

        assertNull(result);
    }

    @Test
    void findById_returnsUserWhenExists() {
        PcAbstractUser user = new PcAbstractUser();
        user.setId(100L);
        user.setTenantId(1L);

        when(abstractUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        PcAbstractUser result = service.findById(1L, 100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void getUserTypeValue_returnsTypeValueWhenExists() {
        PcTypeDefinition typeDef = new PcTypeDefinition();
        typeDef.setId(2L);
        typeDef.setTypeKey(PermissionConstants.TYPE_KEY_USER_TYPE);
        typeDef.setTypeValue(2);

        when(typeDefinitionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(typeDef);

        Integer result = service.getUserTypeValue("sys_user");

        assertEquals(2, result);
    }

    @Test
    void getUserTypeValue_returnsDefaultWhenNotExists() {
        when(typeDefinitionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Integer result = service.getUserTypeValue("unknown_type");

        assertEquals(1, result);
    }
}
