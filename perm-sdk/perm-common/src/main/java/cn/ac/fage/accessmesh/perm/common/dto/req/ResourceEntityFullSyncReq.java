package cn.ac.fage.accessmesh.perm.common.dto.req;

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
