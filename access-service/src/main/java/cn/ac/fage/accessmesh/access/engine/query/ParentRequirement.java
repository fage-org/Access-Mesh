package cn.ac.fage.accessmesh.access.engine.query;

import java.util.Set;

/**
 * 父要求（T-PERM-082，设计 §4.5）。
 * <p>
 * 父要求只支持一层（不能再嵌套父）；绑定的是父授权记录，不是父资源。
 * 空父操作集不代表不限操作——结构校验直接拒绝空集。
 * 首版混批约束：同次请求至多一个不同的父要求，同值判定按字段字面值判等
 * （如 ByCode 的 codeType=null 与空串视为不同值），调用方负责构造前归一。
 * 集合为 null 或含 null 元素属编程错误（构造期 NPE）；空集合属结构错误（执行前拒绝）。
 * </p>
 *
 * @param resourceTypeCode 父资源类型码，非空
 * @param resource         父资源引用，非空
 * @param operationCodes   父操作码集合，非空集合
 */
public record ParentRequirement(String resourceTypeCode, ResourceRef resource, Set<String> operationCodes) {

    public ParentRequirement {
        operationCodes = Set.copyOf(operationCodes);
    }
}
