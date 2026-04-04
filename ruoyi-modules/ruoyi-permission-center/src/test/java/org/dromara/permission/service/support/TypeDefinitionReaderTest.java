package org.dromara.permission.service.support;

import org.dromara.permission.domain.PcTypeDefinition;
import org.dromara.permission.mapper.PcTypeDefinitionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TypeDefinitionReaderTest {

    @Mock
    private PcTypeDefinitionMapper mapper;

    @InjectMocks
    private TypeDefinitionReader reader;

    @Test
    void existsTypeValue_domainMatch_returnsTrueWithoutFallback() {
        when(mapper.selectOne(any())).thenReturn(new PcTypeDefinition());

        boolean result = reader.existsTypeValue(1L, 10L, "role_type", 1);

        assertTrue(result);
        verify(mapper, times(1)).selectOne(any());
    }

    @Test
    void existsTypeValue_domainMiss_globalFallbackReturnsTrue() {
        when(mapper.selectOne(any())).thenReturn(null, new PcTypeDefinition());

        boolean result = reader.existsTypeValue(1L, 10L, "role_type", 1);

        assertTrue(result);
        verify(mapper, times(2)).selectOne(any());
    }

    @Test
    void existsTypeValue_noDomain_globalOnly() {
        when(mapper.selectOne(any())).thenReturn(new PcTypeDefinition());

        boolean result = reader.existsTypeValue(1L, null, "user_type", 2);

        assertTrue(result);
        verify(mapper, times(1)).selectOne(any());
    }

    @Test
    void existsTypeValue_noMatch_returnsFalse() {
        when(mapper.selectOne(any())).thenReturn((PcTypeDefinition) null, (PcTypeDefinition) null);

        boolean result = reader.existsTypeValue(1L, 10L, "resource_type", 3);

        assertFalse(result);
    }

    @Test
    void assertTypeValueExists_noMatch_throws() {
        when(mapper.selectOne(any())).thenReturn((PcTypeDefinition) null, (PcTypeDefinition) null);

        assertThrows(IllegalArgumentException.class,
            () -> reader.assertTypeValueExists(1L, 10L, "role_type", 1, "invalid"));
    }

    @Test
    void assertTypeValueExists_match_doesNotThrow() {
        when(mapper.selectOne(any())).thenReturn(new PcTypeDefinition());

        assertDoesNotThrow(() ->
            reader.assertTypeValueExists(1L, null, "user_type", 1, "invalid"));
    }
}
