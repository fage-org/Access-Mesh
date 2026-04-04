package org.dromara.permission.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class TypeDefinitionSaveReq {
    private List<TypeDefinitionItem> items;

    @Data
    public static class TypeDefinitionItem {
        private Long id;
        private Long tenantId;
        private Long bizDomainId;
        private String typeKey;
        private Integer typeValue;
        private String name;
        private String description;
        private Integer sortOrder;
    }
}
