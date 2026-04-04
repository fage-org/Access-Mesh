package org.dromara.permission.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.permission.domain.PcTypeDefinition;
import org.dromara.permission.mapper.PcTypeDefinitionMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TypeDefinitionReader {

    private final PcTypeDefinitionMapper mapper;

    public List<PcTypeDefinition> listByTenantAndTypeKey(Long tenantId, String typeKey) {
        if (tenantId == null || typeKey == null || typeKey.isBlank()) {
            return Collections.emptyList();
        }
        return mapper.selectByTenantAndTypeKey(tenantId, typeKey);
    }

    public boolean existsTypeValue(Long tenantId, String typeKey, Integer typeValue) {
        return existsTypeValue(tenantId, null, typeKey, typeValue);
    }

    public boolean existsTypeValue(Long tenantId, Long bizDomainId, String typeKey, Integer typeValue) {
        if (tenantId == null || typeValue == null || typeKey == null || typeKey.isBlank()) {
            return false;
        }
        if (bizDomainId != null) {
            PcTypeDefinition domainScoped = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PcTypeDefinition>()
                .eq(PcTypeDefinition::getTenantId, tenantId)
                .eq(PcTypeDefinition::getBizDomainId, bizDomainId)
                .eq(PcTypeDefinition::getTypeKey, typeKey)
                .eq(PcTypeDefinition::getTypeValue, typeValue)
                .eq(PcTypeDefinition::getDeleteFlag, 0L)
                .last("LIMIT 1"));
            if (domainScoped != null) {
                return true;
            }
        }
        return mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PcTypeDefinition>()
            .eq(PcTypeDefinition::getTenantId, tenantId)
            .isNull(PcTypeDefinition::getBizDomainId)
            .eq(PcTypeDefinition::getTypeKey, typeKey)
            .eq(PcTypeDefinition::getTypeValue, typeValue)
            .eq(PcTypeDefinition::getDeleteFlag, 0L)
            .last("LIMIT 1")) != null;
    }

    public void assertTypeValueExists(Long tenantId, String typeKey, Integer typeValue, String message) {
        assertTypeValueExists(tenantId, null, typeKey, typeValue, message);
    }

    public void assertTypeValueExists(Long tenantId, Long bizDomainId, String typeKey, Integer typeValue, String message) {
        if (!existsTypeValue(tenantId, bizDomainId, typeKey, typeValue)) {
            throw new IllegalArgumentException(message);
        }
    }
}
