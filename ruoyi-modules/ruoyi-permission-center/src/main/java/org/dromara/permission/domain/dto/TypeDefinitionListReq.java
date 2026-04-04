package org.dromara.permission.domain.dto;

import lombok.Data;

@Data
public class TypeDefinitionListReq {
    private Long tenantId;
    private Long bizDomainId;
    private String typeKey;
}
