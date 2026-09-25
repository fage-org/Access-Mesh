package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Map;
import java.util.Set;

/**
 * 执行覆盖信息（T-PERM-082，设计 §3.3）。
 * <p>
 * 表达真实执行情况：主体解析方式、条件/互斥评估状态、父检查状态、
 * 实际完成与被短路阶段（不为美化解释补查未执行阶段）、
 * 事实收集完整性与授权阶段。准入的互斥 SKIPPED 标明延后业务，不写通过。
 * </p>
 *
 * @param subjectResolution          主体解析方式
 * @param conditions                 条件评估覆盖
 * @param permissionMutex            权限互斥覆盖
 * @param parentCheck                父检查覆盖（准入运行时父判定为 RUNTIME_DEFERRED）
 * @param completedStages            实际完成阶段；null 归一为空集
 * @param skippedStages              被短路阶段及理由；null 归一为空 Map
 * @param requestedSelectionComplete 事实收集是否完整（FACTS 成功必须为 true）
 * @param authorizationStage         授权阶段（最终判定/事实收集/操作准入）
 */
public record EvaluationCoverage(SubjectResolution subjectResolution, ConditionCoverage conditions,
                                 MutexCoverage permissionMutex, ParentCheckCoverage parentCheck,
                                 Set<Stage> completedStages, Map<Stage, SkipReason> skippedStages,
                                 boolean requestedSelectionComplete, AuthorizationStage authorizationStage) {

    public EvaluationCoverage {
        completedStages = completedStages == null ? Set.of() : Set.copyOf(completedStages);
        skippedStages = skippedStages == null ? Map.of() : Map.copyOf(skippedStages);
    }

    /** 主体解析方式（§2.2）。 */
    public enum SubjectResolution {

        /** User 主体：有效角色＋ROLE_MUTEX 共同入口解析。 */
        USER_EFFECTIVE_WITH_MUTEX,

        /** Roles 主体：显式角色视角，原样采用、不解析用户。 */
        EXPLICIT_ROLES
    }

    /** 条件评估覆盖（§3.3）。 */
    public enum ConditionCoverage {

        /** 已按当前可信环境评估。 */
        EVALUATED,

        /** 保留条件事实未过滤（PRESERVE）。 */
        PRESERVED,

        /** 无候选条件可评估。 */
        NO_CANDIDATE
    }

    /** 权限互斥覆盖（§3.3；准入 SKIPPED 并标明延后业务，不写通过）。 */
    public enum MutexCoverage {

        /** 已对本项候选执行互斥。 */
        EVALUATED,

        /** 按合法组合跳过（准入延后业务最终检查）。 */
        SKIPPED,

        /** 无候选可执行互斥。 */
        NO_CANDIDATE
    }

    /** 父检查覆盖（§3.3）。 */
    public enum ParentCheckCoverage {

        /** 选择不携带父要求。 */
        NOT_REQUIRED,

        /** 携带父要求但未触发（如无子候选/NO_ROLE 短路）。 */
        NOT_TRIGGERED,

        /** 父判定通过。 */
        PASSED,

        /** 父判定失败（GRANT_LIST 整集合门禁）。 */
        FAILED,

        /** 准入用途：运行时父校验延后至业务。 */
        RUNTIME_DEFERRED
    }

    /** 阶段短路理由（§3.3 实际完成与短路理由；值随阶段落地按真实需要扩充）。 */
    public enum SkipReason {

        /** 无有效角色，主体阶段短路。 */
        NO_ROLE
    }

    /** 授权阶段（§3.3；准入恒 finalCheckRequired）。 */
    public enum AuthorizationStage {

        /** 普通最终判定。 */
        FINAL_DECISION,

        /** 授权事实收集。 */
        FACT_COLLECTION,

        /** 操作准入。 */
        OPERATION_ADMISSION
    }
}
