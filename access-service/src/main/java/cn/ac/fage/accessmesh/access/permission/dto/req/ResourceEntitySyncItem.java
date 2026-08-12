package cn.ac.fage.accessmesh.access.permission.dto.req;

import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 资源实体 full-sync 单条 item。
 */
public record ResourceEntitySyncItem(
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
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
}
