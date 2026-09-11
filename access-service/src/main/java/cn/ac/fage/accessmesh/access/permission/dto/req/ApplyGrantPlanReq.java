package cn.ac.fage.accessmesh.access.permission.dto.req;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 角色权限聚合变更请求。
 *
 * <p>creates、updates、removes 在同一事务中原子执行。条件双轨制（T-PERM-048）：
 * 条件绑定形态二选一——{@code conditionCode}（引用管理页条件，值域=MANAGED）或
 * {@code inlineCondition}（内联定义，随计划同事务创建/回收），同记录同时出现拒绝。</p>
 */
public record ApplyGrantPlanReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    @NotNull @Valid GrantPlan plan
) {

    /**
     * 授权页内联条件定义（T-PERM-048 定案①）。
     *
     * <p>内联条件 1:1 属于其授权记录：随 apply-grant-plan 同事务创建（source=INLINE、
     * code 自动生成 inline- 前缀、enabled 恒 true），授权行删除/换绑时引用归零同事务回收。
     * 门禁随授权入口 ROLE:MANAGE 携带（定案②），不单独要求 CONDITION 写权限。</p>
     *
     * @param name              条件名称（必填，DDL NOT NULL）
     * @param conditionRules    条件规则 JSON（必填，写入口径校验同管理页轨）
     * @param gatewayEvaluable  可下发 Gateway 评估（缺省 false）
     */
    public record InlineConditionDef(
        @NotBlank @Size(max = 128) String name,
        @NotBlank String conditionRules,
        Boolean gatewayEvaluable
    ) {}

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
        @Valid InlineConditionDef inlineCondition,
        Boolean canGrant
    ) {}

    public record UpdateItem(
        @NotNull @Positive Long id,
        Boolean canGrant,
        String conditionCode,
        @Valid InlineConditionDef inlineCondition
    ) {}
}
