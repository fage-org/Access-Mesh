package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 冲突检测结果响应体
 * <p>
 * 返回权限冲突检测的结果，包括是否检测到冲突和匹配的冲突规则列表。
 * 仅用于检测，不持久化数据。
 * </p>
 *
 * @param conflictDetected 是否检测到冲突
 * @param matchedRules     匹配的冲突规则列表
 */
public record ConflictDetectResp(
    boolean conflictDetected,
    List<ConflictRuleResp> matchedRules
) {}