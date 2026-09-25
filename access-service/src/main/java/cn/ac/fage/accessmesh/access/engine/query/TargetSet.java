package cn.ac.fage.accessmesh.access.engine.query;

import java.util.List;

/**
 * 目标集合选择（T-PERM-082，设计 §2.4/§4.4）。
 * <p>
 * 每个 clause 的目标/允许祖先授权联合成<b>本项</b>候选（共同集合语义）；
 * 不是逐 clause 先求 boolean 再 OR/AND。类型级回退独立配置；
 * 父要求存在时按 §4.5 惰性计算（骨架期不评估）。clauses 为空属结构错误（执行前拒绝）；
 * 集合为 null 或含 null 元素属编程错误（构造期 NPE）。
 * </p>
 *
 * @param clauses      目标子句集合，非空集合
 * @param inheritance  判定面继承模式，非空（结构校验）
 * @param typeFallback 类型级回退策略，非空（结构校验）
 * @param parent       父要求，可空（无父=排除 depend_on 子行、主行照常）
 */
public record TargetSet(List<TargetClause> clauses, Inheritance inheritance,
                        TypeFallback typeFallback, ParentRequirement parent) implements Selection {

    public TargetSet {
        clauses = List.copyOf(clauses);
    }
}
