package cn.ac.fage.accessmesh.access.engine.query;

/**
 * LIST 授权事实读取来源（T-PERM-082，设计 §2.1/§5.2/§9.1）。
 * <p>
 * 只开放已有明确需要的 LIST 授权来源，不提供含糊的 allFresh；
 * {@code bypassPermSnapshot}（现行转授通道）映射为 {@link #DATABASE}。
 * </p>
 */
public enum ListGrantRead {

    /** 数据库直读，不读、不回填角色授权快照。 */
    DATABASE,

    /** 既有 ROLE_PERM_SNAPSHOT 缓存来源（仅保存原始行）。 */
    ROLE_SNAPSHOT
}
