package cn.ac.fage.accessmesh.access.sync.dto;

import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq;
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
        @NotNull @Valid SyncVersionRef syncVersion,
        String publicationGeneration
) {
    /** 纯增量旧调用方保持原 Java 构造形态；不替调用方生成代次。 */
    public ResourceEntitySyncReq(String operation, String resourceTypeCode, String resourceCode, String codeType,
            String name, String parentResourceTypeCode, String parentResourceCode, String parentCodeType,
            String path, Integer status, Map<String, Object> extra, String sourceService,
            String sourceEntityType, String sourceEntityId, SyncVersionRef syncVersion) {
        this(operation, resourceTypeCode, resourceCode, codeType, name, parentResourceTypeCode, parentResourceCode,
                parentCodeType, path, status, extra, sourceService, sourceEntityType, sourceEntityId, syncVersion, null);
    }

    /**
     * 归一编码类型：null/空白 → default，去首尾空白（T-PERM-100，与管理面
     * {@code ResourceKeyReq.normalizedCodeType} 同口径）——写入、寻址与业务键构造统一入口。
     */
    public static String normalizeCodeType(String codeType) {
        return codeType == null || codeType.isBlank() ? ResourceKeyReq.CODE_TYPE_DEFAULT : codeType.trim();
    }

    /** 归一后的编码类型。 */
    public String normalizedCodeType() {
        return normalizeCodeType(codeType);
    }

    /** 归一后的父编码类型（父解析寻址统一口径）。 */
    public String normalizedParentCodeType() {
        return normalizeCodeType(parentCodeType);
    }
}
