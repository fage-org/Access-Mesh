package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;

import java.util.List;

/**
 * 操作定义「专属优先、全局回退」解析（api-contract §5.3 与 §6.5 operationCode 适用性校验的
 * <b>同一解析实现</b>，契约禁止两套逻辑）。
 * <p>
 * 唯一职责：对<b>已加载</b>的操作定义集合做纯内存合并/解析，不查库——调用方各自决定加载方式
 * （列表接口单查全量、授权计划预检一次装载后逐记录解析），避免 N+1。
 * </p>
 */
public interface OperationResolutionDomainService {

    /**
     * 「专属优先、全局回退」合并（api-contract §5.3）。
     *
     * @param operations   全量操作定义（含各类型专属与全局 resource_type=null）
     * @param resourceType 目标资源类型值；null 表示无专属侧（结果 = 仅全局集合）
     * @return resourceType 非空：该类型专属定义 ∪ 无同码冲突的全局定义（同码专属优先，全局被剔除）；
     *         resourceType 为空：仅全局定义集合。码比较统一经 {@link #normalizeCode(String)}
     */
    List<OperationPermission> mergeGlobalFallback(List<OperationPermission> operations, Integer resourceType);

    /**
     * 操作码归一化（trim + 大写）——合并去重与按码解析共用同一口径。
     */
    static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
