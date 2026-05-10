package cn.ac.fage.accessmesh.permission.service.domain.sync;

/**
 * 资源同步结果类
 * <p>
 * 用于统计资源同步操作的结果数据。
 * 包含创建数、更新数、活跃资源ID集合。
 * </p>
 */
public class SyncResourcesResult {
    /**
     * 创建的资源数量
     */
    private int createdCount;

    /**
     * 更新的资源数量
     */
    private int updatedCount;

    /**
     * 活跃资源ID集合（用于后续清理判断）
     */
    private java.util.Set<Long> activeResourceIds;

    /**
     * 默认构造函数
     */
    public SyncResourcesResult() {
        this.activeResourceIds = new java.util.HashSet<>();
    }

    /**
     * 全参数构造函数
     *
     * @param createdCount     创建数
     * @param updatedCount     更新数
     * @param activeResourceIds 活跃资源ID集合
     */
    public SyncResourcesResult(int createdCount, int updatedCount, java.util.Set<Long> activeResourceIds) {
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.activeResourceIds = activeResourceIds != null ? activeResourceIds : new java.util.HashSet<>();
    }

    // ===== Getters and Setters =====

    /**
     * 获取创建数
     */
    public int getCreatedCount() { return createdCount; }

    /**
     * 设置创建数
     */
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }

    /**
     * 获取更新数
     */
    public int getUpdatedCount() { return updatedCount; }

    /**
     * 设置更新数
     */
    public void setUpdatedCount(int updatedCount) { this.updatedCount = updatedCount; }

    /**
     * 获取活跃资源ID集合
     */
    public java.util.Set<Long> getActiveResourceIds() { return activeResourceIds; }

    /**
     * 设置活跃资源ID集合
     */
    public void setActiveResourceIds(java.util.Set<Long> activeResourceIds) {
        this.activeResourceIds = activeResourceIds != null ? activeResourceIds : new java.util.HashSet<>();
    }
}