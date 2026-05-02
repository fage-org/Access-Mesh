package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record ConflictRuleDetailReq(
    @NotNull(message = "冲突规则ID不能为空") Long conflictRuleId
) {}