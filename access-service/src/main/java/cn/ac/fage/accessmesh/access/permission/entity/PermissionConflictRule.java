package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 权限冲突规则实体
 * <p>
 * 表示权限冲突检测的规则配置。
 * 定义两种操作权限或角色之间的冲突关系，
 * 用于在授权时检测和防止权限冲突。
 * 冲突类型包括：互斥操作、角色互斥等。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("permission_conflict_rule")
public class PermissionConflictRule {

    /**
     * 权限冲突规则唯一标识
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 冲突类型（MUTEX_OP=操作互斥，MUTEX_ROLE=角色互斥）
     */
    private String conflictType;

    /**
     * 第一个操作权限ID
     */
    private Long firstOperationPermissionId;

    /**
     * 第二个操作权限ID
     */
    private Long secondOperationPermissionId;

    /**
     * 资源类型值
     */
    private Integer resourceTypeValue;

    /**
     * 第一个抽象角色ID
     */
    private Long firstAbstractRoleId;

    /**
     * 第二个抽象角色ID
     */
    private Long secondAbstractRoleId;

    /**
     * 规则描述，说明冲突场景和处理方式
     */
    private String description;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

    /**
     * 最后更新者用户ID
     */
    private Long updatedBy;

    /**
     * 删除者用户ID
     */
    private Long deletedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，其他=已删除）
     */
    private Long deleteFlag;
}