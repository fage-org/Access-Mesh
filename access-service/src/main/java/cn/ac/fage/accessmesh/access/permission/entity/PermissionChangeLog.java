package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 权限变更日志实体
 * <p>
 * 表示权限变更的详细记录日志。
 * 记录变更前后数据快照、差异信息、受影响的用户和角色等。
 * 用于权限审计、变更追溯和合规性检查。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("permission_change_log")
public class PermissionChangeLog {

    /**
     * 权限变更日志唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 实体类型（ROLE/RESOURCE/PERMISSION等）
     */
    private String entityType;

    /**
     * 实体ID
     */
    private Long entityId;

    /**
     * 操作类型（CREATE/UPDATE/DELETE）
     */
    private String operation;

    /**
     * 变更前数据快照（JSON格式）
     */
    private String oldSnapshot;

    /**
     * 变更后数据快照（JSON格式）
     */
    private String newSnapshot;

    /**
     * 差异快照（JSON格式），记录变更的具体差异
     */
    private String diffSnapshot;

    /**
     * 受影响的用户ID数组
     */
    private Long[] affectedAbstractUserIds;

    /**
     * 受影响的角色ID数组
     */
    private Long[] affectedAbstractRoleIds;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 变更来源（MANUAL/SYNC/API等）
     */
    private String changeSource;

    /**
     * 请求ID，用于关联请求链路
     */
    private String requestId;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}