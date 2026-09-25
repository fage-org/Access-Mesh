package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 请求级读取选项（T-PERM-082，设计 §2.1/§5.2）。
 * <p>
 * 缺省来源为 {@link ListGrantRead#ROLE_SNAPSHOT}（沿现行 LIST 默认来源）；
 * 其他数据来源由固定读取矩阵决定，请求层不开放逐项选择。
 * </p>
 *
 * @param listGrantRead LIST 授权事实来源；null 归一为 ROLE_SNAPSHOT
 */
public record ReadOptions(ListGrantRead listGrantRead) {

    public ReadOptions {
        listGrantRead = listGrantRead == null ? ListGrantRead.ROLE_SNAPSHOT : listGrantRead;
    }

    /** 缺省读取选项（LIST 来源=ROLE_SNAPSHOT）。 */
    public static ReadOptions defaults() {
        return new ReadOptions(ListGrantRead.ROLE_SNAPSHOT);
    }
}
