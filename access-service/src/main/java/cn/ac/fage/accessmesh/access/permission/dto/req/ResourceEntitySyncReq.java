package cn.ac.fage.accessmesh.access.permission.dto.req;

import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 资源实体同步请求
 * <p>
 * 详见 docs/design/permission-center/api-contract.md §6.2.2。
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
        Integer sortOrder,
        Map<String, Object> extra,
        @NotBlank String sourceService,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
