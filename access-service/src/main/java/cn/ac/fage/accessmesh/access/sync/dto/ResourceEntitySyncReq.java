package cn.ac.fage.accessmesh.access.sync.dto;

import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 资源实体同步请求
 * <p>
 * 详见 docs/design/access-service-api-contract.md §19.1。
 * </p>
 */
public record ResourceEntitySyncReq(
        @NotBlank String operation,
        @NotBlank String resourceTypeCode,
        @NotBlank String resourceCode,
        String codeType,
        String name,
        String parentResourceTypeCode,
        String parentResourceCode,
        String parentCodeType,
        String path,
        Integer status,
        Map<String, Object> extra,
        @NotBlank String sourceService,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
