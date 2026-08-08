package cn.ac.fage.accessmesh.permission.dto.req;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 角色权限聚合变更请求。
 *
 * <p>creates、updates、removes 在同一事务中原子执行。</p>
 */
public record ApplyGrantPlanReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    @NotNull @Valid GrantPlan plan
) {

    public record GrantPlan(
        List<@NotNull @Valid CreateItem> creates,
        List<@NotNull @Valid UpdateItem> updates,
        List<@NotNull @Positive Long> removes
    ) {
        public List<CreateItem> createItems() {
            return creates == null ? List.of() : creates;
        }

        public List<UpdateItem> updateItems() {
            return updates == null ? List.of() : updates;
        }

        public List<Long> removeIds() {
            return removes == null ? List.of() : removes;
        }
    }

    public record CreateItem(
        @NotNull @Valid GrantRecordKey key,
        @Positive Long parentPermissionId,
        List<@NotNull @Valid GrantRecordKey> children
    ) {
        public List<GrantRecordKey> childItems() {
            return children == null ? List.of() : children;
        }
    }

    public record GrantRecordKey(
        @NotBlank String resourceTypeCode,
        String resourceCode,
        String codeType,
        @NotBlank String operationCode,
        @NotNull ScopeMode scopeMode,
        String conditionCode,
        Boolean canGrant
    ) {}

    public record UpdateItem(
        @NotNull @Positive Long id,
        Boolean canGrant,
        String conditionCode
    ) {}
}
