package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Set;

/**
 * 输出规格（T-PERM-082，设计 §3.3）。
 * <p>
 * 仅控制事实保留、描述、投影与 TRACE；不能关闭判定必需计算。
 * extraOperationKeys 只供描述/投影，不能扩大 Selection。
 * FACTS 至少 KEPT；范围必须 RAW_AND_KEPT；最小 DECISION/ADMISSION 可为 NONE。
 * </p>
 *
 * @param factDetail            事实档位，非空
 * @param matchedIds            是否输出命中角色/权限 id
 * @param descriptions          是否输出描述块
 * @param effectiveOperations   是否输出有效操作展开
 * @param presentationExpansion 展示方向 NONE/PARENTS/CHILDREN/BOTH（与判定面继承分离）
 * @param extraOperationKeys    额外类型—操作配对（仅描述/投影）；null 归一为空集
 * @param trace                 是否输出 TRACE（仅受权诊断可用，门禁在应用层）
 */
public record OutputSpec(FactDetail factDetail, boolean matchedIds, boolean descriptions,
                         boolean effectiveOperations, PresentationExpansion presentationExpansion,
                         Set<TypeOperation> extraOperationKeys, boolean trace) {

    public OutputSpec {
        extraOperationKeys = extraOperationKeys == null ? Set.of() : Set.copyOf(extraOperationKeys);
    }

    /** 最小输出：无事实、无描述，判定必需计算不受影响。 */
    public static OutputSpec minimal() {
        return new OutputSpec(FactDetail.NONE, false, false, false, PresentationExpansion.NONE, Set.of(), false);
    }

    /** 最小输出＋命中 ID（A.1 普通最终鉴权缺省形态）。 */
    public static OutputSpec minimalWithMatchIds() {
        return new OutputSpec(FactDetail.NONE, true, false, false, PresentationExpansion.NONE, Set.of(), false);
    }

    /** 事实输出：保留评估后事实＋命中 ID。 */
    public static OutputSpec kept() {
        return new OutputSpec(FactDetail.KEPT, true, false, false, PresentationExpansion.NONE, Set.of(), false);
    }

    /** 范围输出：raw＋retained 双轨（queryScopes 四态投影必需）。 */
    public static OutputSpec rawAndKept() {
        return new OutputSpec(FactDetail.RAW_AND_KEPT, true, false, false, PresentationExpansion.NONE, Set.of(), false);
    }

    /** 完整输出：全档事实＋描述＋操作展开＋展示展开＋TRACE。 */
    public static OutputSpec full() {
        return new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, true, PresentationExpansion.BOTH, Set.of(), true);
    }
}
