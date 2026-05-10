package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 冲突规则详情查询请求体
 * <p>
 * 用于查询冲突规则的详细信息，使用规则ID标识。
 * </p>
 *
 * @param conflictRuleId 冲突规则ID，必填
 */
public record ConflictRuleDetailReq(
    @NotNull(message = "冲突规则ID不能为空") Long conflictRuleId
) {}