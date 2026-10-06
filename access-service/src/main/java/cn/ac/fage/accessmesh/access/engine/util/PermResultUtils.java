package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.query.DecisionResult;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;

import java.util.*;

/**
 * 权限结果转换工具类
 * <p>
 * 提供权限查询结果转换为各种响应DTO的静态方法。
 * toAuthCheckResp 为新
 * {@link DecisionResult} → 既有外部响应的纯转换（不经中间结果对象，设计 §9.1）。
 * </p>
 * <p>
 * T-API-002（2026-09-06）check 族响应内部 id 字段族裁剪已被 T-API-003（2026-09-09）
 * 推翻：check 族端点恢复结果记录全量回传（matchedRoleIds / matchedPermissionIds /
 * matchedResources[].resourceId），本工具类恢复产出该字段族；需要 matched id 集合
 * 的内部场景直接消费引擎结果不经线格式中转。零调用的 toQueryResourcesResp 已删除
 * （真实组装在 PermissionQueryAppServiceImpl.buildQueryResourcesResponse）。
 * </p>
 */
public final class PermResultUtils {

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private PermResultUtils() {}

    // ===== DTO转换 =====

    /**
     * 转换新 DecisionResult 为 AuthCheckResp
     * <p>
     * 将新引擎单项最终判定转换为权限校验响应 DTO（纯转换，T-PERM-089）。
     * 拒绝原因取枚举 name；允许/拒绝的条件参与状态同源来自上下文筛选后的候选事实，
     * 不能从评估后保留集合判断（条件失败与互斥会移除这些事实）。拒绝命中 ID 恒为空。
     * </p>
     *
     * @param r 新引擎单项最终判定结果
     * @return 权限校验响应
     */
    public static AuthCheckResp toAuthCheckResp(DecisionResult r) {
        boolean conditionEvaluated = r.details().stageFacts().stream()
            .flatMap(facts -> facts.rawAfterContext().stream())
            .anyMatch(GrantFact::hasCondition);
        if (r.outcome() == DecisionResult.Decision.DENY) {
            return AuthCheckResp.deny(r.reason().name(), conditionEvaluated);
        }
        return AuthCheckResp.allow(
            r.details().matchedRoleIds().stream().toList(),
            r.details().matchedPermissionIds().stream().toList(), conditionEvaluated);
    }

}
