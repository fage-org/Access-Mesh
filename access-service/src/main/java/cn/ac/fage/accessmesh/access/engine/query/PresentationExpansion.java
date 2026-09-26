package cn.ac.fage.accessmesh.access.engine.query;

/** 只控制评估后的展示方向，与判定面 Inheritance 独立。 */
public enum PresentationExpansion {
    NONE, PARENTS, CHILDREN, BOTH;

    boolean parents() { return this == PARENTS || this == BOTH; }
    boolean children() { return this == CHILDREN || this == BOTH; }
}
