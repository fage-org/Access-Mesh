package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 资源实体全量同步请求。
 */
public record ResourceEntityFullSyncReq(
        @NotNull @Valid ResourceEntitySyncScope scope,
        @NotEmpty @Valid List<ResourceEntitySyncItem> items
) {
}
