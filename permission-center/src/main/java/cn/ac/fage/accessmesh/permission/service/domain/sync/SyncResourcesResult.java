package cn.ac.fage.accessmesh.permission.service.domain.sync;

import java.util.HashSet;
import java.util.Set;

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
    private Set<Long> activeResourceIds;

    /**
     * 默认构造函数
     */
    public SyncResourcesResult() {
        this.activeResourceIds = new HashSet<>();
    }

    /**
     * 全参数构造函数
     *
     * @param createdCount     创建数
     * @param updatedCount     更新数
     * @param activeResourceIds 活跃资源ID集合
     */
    public SyncResourcesResult(int createdCount, int updatedCount, Set<Long> activeResourceIds) {
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.activeResourceIds = activeResourceIds != null ? activeResourceIds : new HashSet<>();
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public Set<Long> getActiveResourceIds() {
        return activeResourceIds;
    }

    public void setActiveResourceIds(Set<Long> activeResourceIds) {
        this.activeResourceIds = activeResourceIds != null ? activeResourceIds : new HashSet<>();
    }
}