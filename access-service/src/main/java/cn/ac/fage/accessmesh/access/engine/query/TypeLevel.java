package cn.ac.fage.accessmesh.access.engine.query;

import java.util.List;

/**
 * 类型级选择（T-PERM-082，设计 §2.4/§4.3）。
 * <p>
 * 仅适用类型级主授权（排除 depend_on 子行）；一项内全部 requirements 构成一个候选集合，
 * 经评估后有保留事实即成立——不是「持有任一实例权限就通过」，
 * 也不是所有 requirements 必须分别通过。requirements 为空属结构错误（执行前拒绝）；
 * 集合为 null 或含 null 元素属编程错误（构造期 NPE）。
 * </p>
 *
 * @param requirements 类型—操作要求集合，非空集合
 */
public record TypeLevel(List<TypeOperation> requirements) implements Selection {

    public TypeLevel {
        requirements = List.copyOf(requirements);
    }
}
