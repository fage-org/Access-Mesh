package cn.ac.fage.accessmesh.permission.service.domain.sync;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * 映射同步结果类
 * <p>
 * 用于统计资源-API映射同步操作的结果数据。
 * 包含创建数、更新数、传入映射键集合（用于后续清理判断）。
 * </p>
 */
@Getter
@Setter
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
    private Set<String> incomingKeys;

    /**
     * 默认构造函数
     */
    public SyncMappingsResult() {
        this.incomingKeys = new HashSet<>();
    }

    /**
     * 全参数构造函数
     *
     * @param createdCount  创建数
     * @param updatedCount  更新数
     * @param incomingKeys  传入映射键集合
     */
    public SyncMappingsResult(int createdCount, int updatedCount, Set<String> incomingKeys) {
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.incomingKeys = incomingKeys != null ? incomingKeys : new HashSet<>();
    }

    public void setIncomingKeys(Set<String> incomingKeys) {
        this.incomingKeys = incomingKeys != null ? incomingKeys : new HashSet<>();
    }
}