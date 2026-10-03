package cn.ac.fage.accessmesh.access.sync.dto;

import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
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
        Map<String, Object> extra,
        String sourceEntityType,
        String sourceEntityId,
        @NotNull @Valid SyncVersionRef syncVersion
) {
    /** 归一后的编码类型（T-PERM-100：null/空白→default、去首尾空白，与管理面同口径）。 */
    public String normalizedCodeType() {
        return ResourceEntitySyncReq.normalizeCodeType(codeType);
    }

    /** 归一后的父编码类型（父解析寻址统一口径）。 */
    public String normalizedParentCodeType() {
        return ResourceEntitySyncReq.normalizeCodeType(parentCodeType);
    }
}
