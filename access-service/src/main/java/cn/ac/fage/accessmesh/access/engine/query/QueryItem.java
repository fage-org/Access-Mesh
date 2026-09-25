package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Objects;

/**
 * 查询单项（T-PERM-082，设计 §2.1）。
 * <p>
 * 单条就是一个 item，独立批量就是多个 item；key 唯一（重复为结构错误），
 * 相同目标可出现多项、各自返回。评估策略与结果形式的合法配对受
 * {@code QueryRequestValidator} 执行前整体校验；受控工厂构造合法形态的便捷入口，
 * 不替代校验（直接经 canonical 构造的非法组合同样在执行前拒绝）。
 * </p>
 *
 * @param key        项键，非空、同请求内唯一
 * @param selection  四种选择之一，非空
 * @param evaluation 评估策略对，非空
 * @param resultForm 结果完成目标，非空
 * @param output     输出规格，非空
 */
public record QueryItem(String key, Selection selection, Evaluation evaluation,
                        ResultForm resultForm, OutputSpec output) {

    /** 普通最终鉴权项：TYPE_LEVEL/TARGET_SET＋EVALUATE+ENFORCE＋DECISION（C02 合法唯一形态）。 */
    public static QueryItem decision(String key, Selection selection, OutputSpec output) {
        Objects.requireNonNull(selection, "selection 不能为空");
        if (selection instanceof GrantList || selection instanceof OperationAdmission) {
            throw new IllegalArgumentException("DECISION 仅支持 TYPE_LEVEL/TARGET_SET 选择: " + selection.getClass().getSimpleName());
        }
        return new QueryItem(key, selection, Evaluation.full(), ResultForm.DECISION, output);
    }

    /** 普通事实项：TYPE_LEVEL/TARGET_SET/GRANT_LIST＋FACTS（评估策略按矩阵自由组合）。 */
    public static QueryItem facts(String key, Selection selection, Evaluation evaluation, OutputSpec output) {
        Objects.requireNonNull(selection, "selection 不能为空");
        if (selection instanceof OperationAdmission) {
            throw new IllegalArgumentException("OPERATION_ADMISSION 的事实形态用 admissionFacts 工厂（固定 PRESERVE+SKIP）");
        }
        return new QueryItem(key, selection, evaluation, ResultForm.FACTS, output);
    }

    /** 授权清单事实项：GRANT_LIST＋FACTS（首版单项独占请求）。 */
    public static QueryItem grantListFacts(String key, ParentRequirement requiredParent,
                                           Evaluation evaluation, OutputSpec output) {
        return new QueryItem(key, new GrantList(requiredParent), evaluation, ResultForm.FACTS, output);
    }

    /** 在线操作准入项：OPERATION_ADMISSION＋EVALUATE+SKIP＋ADMISSION（受控准入工厂，恒要求业务最终检查）。 */
    public static QueryItem admission(String key, TypeOperation requirement, OutputSpec output) {
        return new QueryItem(key, new OperationAdmission(requirement),
            Evaluation.evaluateSkip(), ResultForm.ADMISSION, output);
    }

    /** 准入快照候选项：OPERATION_ADMISSION＋PRESERVE+SKIP＋FACTS（收全候选与条件身份，不返回准入布尔）。 */
    public static QueryItem admissionFacts(String key, TypeOperation requirement, OutputSpec output) {
        return new QueryItem(key, new OperationAdmission(requirement),
            Evaluation.preserveSkip(), ResultForm.FACTS, output);
    }
}
