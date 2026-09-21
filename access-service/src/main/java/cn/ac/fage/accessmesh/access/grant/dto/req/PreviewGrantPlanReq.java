package cn.ac.fage.accessmesh.access.grant.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 授撤影响预览请求（T-PERM-073，契约 §11.4.1）。
 *
 * <p>request 复用现役 ApplyGrantPlanReq 全量结构；预览与保存共用同一校验（纯准备），
 * 预览无数据库写入、不创建 INLINE 条件（新内联条件以请求条目位置为临时身份）。</p>
 *
 * @param request  授权计划请求（与 apply-grant-plan 同构）
 * @param maxItems 展示预算（1..2000，缺省 500；只截断展示，不影响完整计算）
 */
public record PreviewGrantPlanReq(
    @NotNull @Valid ApplyGrantPlanReq request,
    @Min(1) @Max(2000) Integer maxItems
) {

    public int resolvedMaxItems() {
        return maxItems == null ? 500 : maxItems;
    }
}
