package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 抽象用户 full-sync 请求。
 *
 * @param scope full-sync 范围
 * @param items 待同步条目
 */
public record AbstractUserFullSyncReq(
        @NotNull @Valid AbstractUserSyncScope scope,
        @NotEmpty @Valid List<AbstractUserSyncItem> items
) {
}
