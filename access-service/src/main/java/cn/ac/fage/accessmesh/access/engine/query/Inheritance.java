package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 判定面继承模式（T-PERM-082，设计 §2.4/§9.1）。
 * <p>
 * 与展示面父/子展开（OutputSpec.presentationExpansion）语义分离，不混用。
 * </p>
 */
public enum Inheritance {

    /** 仅目标自身授权。 */
    SELF,

    /** 目标自身＋同类型祖先闭包授权。 */
    SELF_AND_ANCESTORS
}
