package cn.ac.fage.accessmesh.access.sync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 资源实体全量同步请求。
 */
public record ResourceEntityFullSyncReq(
        @NotNull @Valid ResourceEntitySyncScope scope,
        @NotNull List<@NotNull @Valid ResourceEntitySyncItem> items,
        @NotBlank String publicationGeneration
) {
}
