package cn.ac.fage.accessmesh.permission.service.domain.sync;

/**
 * 映射同步结果类
 * <p>
 * 用于统计资源-API映射同步操作的结果数据。
 * 包含创建数、更新数、传入映射键集合（用于后续清理判断）。
 * </p>
 */
public class SyncMappingsResult {
    /**
     * 创建的映射数量
     */
    private int createdCount;

    /**
     * 更新的映射数量
     */
    private int updatedCount;

    /**
     * 传入的映射键集合（用于后续清理判断）
     */
    private java.util.Set<String> incomingKeys;

    /**
     * 默认构造函数
     */
    public SyncMappingsResult() {
        this.incomingKeys = new java.util.HashSet<>();
    }

    /**
     * 全参数构造函数
     *
     * @param createdCount  创建数
     * @param updatedCount  更新数
     * @param incomingKeys  传入映射键集合
     */
    public SyncMappingsResult(int createdCount, int updatedCount, java.util.Set<String> incomingKeys) {
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.incomingKeys = incomingKeys != null ? incomingKeys : new java.util.HashSet<>();
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
     * 获取传入映射键集合
     */
    public java.util.Set<String> getIncomingKeys() { return incomingKeys; }

    /**
     * 设置传入映射键集合
     */
    public void setIncomingKeys(java.util.Set<String> incomingKeys) {
        this.incomingKeys = incomingKeys != null ? incomingKeys : new java.util.HashSet<>();
    }
}