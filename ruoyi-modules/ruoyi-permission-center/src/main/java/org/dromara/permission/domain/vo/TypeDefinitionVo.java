package org.dromara.permission.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TypeDefinitionVo {
    private Long id;
    private Long tenantId;
    private Long bizDomainId;
    private String typeKey;
    private Integer typeValue;
    private String name;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
