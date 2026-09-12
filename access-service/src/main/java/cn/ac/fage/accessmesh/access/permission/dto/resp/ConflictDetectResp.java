package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 冲突检测结果响应体
 * <p>
 * 返回权限冲突检测的结果，包括是否检测到冲突和匹配的冲突规则列表。
 * 仅用于检测，不持久化数据。
 * 角色对形态（T-PERM-063 扩展）额外回传 conflictedUserIds：
 * 当前有效角色集同时含两角色的用户——conflictDetected 即以该清单非空判定
 * （立规前预检语义：非空 = create/update 将被 20063 存量守卫拒绝）。
 * 操作权限对形态 conflictedUserIds 恒为空列表。
 * </p>
 *
 * @param conflictDetected   是否检测到冲突
 * @param matchedRules       匹配的冲突规则列表
 * @param conflictedUserIds  同时持有互斥角色对的用户ID列表（仅角色对形态非空）
 */
public record ConflictDetectResp(
    boolean conflictDetected,
    List<ConflictRuleResp> matchedRules,
    List<Long> conflictedUserIds
) {}
