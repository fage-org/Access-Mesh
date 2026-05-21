package cn.ac.fage.accessmesh.permquery.domain.model.valueobject;

/**
 * 权限冲突信息值对象
 * <p>
 * 表示两个权限之间的冲突关系，用于向调用方报告冲突情况。
 * 冲突类型包括角色互斥和权限互斥。
 * </p>
 */
public record ConflictInfo(
    Long firstPermissionId,
    Long secondPermissionId,
    Long firstRoleId,
    Long secondRoleId,
    String conflictType
) {

    /**
     * 角色互斥冲突类型
     */
    public static final String ROLE_MUTEX = "ROLE_MUTEX";

    /**
     * 权限互斥冲突类型
     */
    public static final String PERM_MUTEX = "PERM_MUTEX";

    /**
     * 判断是否为角色互斥
     */
    public boolean isRoleMutex() {
        return ROLE_MUTEX.equals(conflictType);
    }

    /**
     * 判断是否为权限互斥
     */
    public boolean isPermMutex() {
        return PERM_MUTEX.equals(conflictType);
    }

    /**
     * 创建角色互斥冲突
     */
    public static ConflictInfo roleMutex(Long firstRoleId, Long secondRoleId,
                                          Long firstPermId, Long secondPermId) {
        return new ConflictInfo(firstPermId, secondPermId, firstRoleId, secondRoleId, ROLE_MUTEX);
    }

    /**
     * 创建权限互斥冲突
     */
    public static ConflictInfo permMutex(Long firstPermId, Long secondPermId) {
        return new ConflictInfo(firstPermId, secondPermId, null, null, PERM_MUTEX);
    }
}